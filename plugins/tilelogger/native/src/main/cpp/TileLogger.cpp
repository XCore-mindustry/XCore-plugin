#include <atomic>
#include <memory>
#include <mutex>
#include "tilelogger_TileLogger.h"
#include "TileLogger.h"

static JournalWriter writer;
static auto active = std::make_unique<TileLogger>(writer);
static std::mutex prepared_mutex;
static std::unordered_map<jlong, std::unique_ptr<TileLogger>> prepared;
static std::atomic<jlong> next_handle{};

class UtfChars {
    JNIEnv* env_;
    jstring value_;
    const char* chars_;

public:
    UtfChars(JNIEnv* env, jstring value) : env_(env), value_(value), chars_(nullptr) {
        if (!value) throw std::invalid_argument("Null string");
        chars_ = env_->GetStringUTFChars(value_, nullptr);
        if (!chars_) throw std::runtime_error("Cannot acquire UTF string");
    }
    ~UtfChars() {
        if (chars_) env_->ReleaseStringUTFChars(value_, chars_);
    }
    std::string string() const { return chars_; }
    DataVec bytes() const {
        auto s = string();
        return DataVec(s.begin(), s.end());
    }
};

static void fail(JNIEnv* env, const std::exception& failure) {
    if (env->ExceptionCheck()) return;
    const char* type = dynamic_cast<const std::invalid_argument*>(&failure)
                           ? "java/lang/IllegalArgumentException"
                           : "java/lang/IllegalStateException";
    jclass cls = env->FindClass(type);
    if (cls) {
        env->ThrowNew(cls, failure.what());
        env->DeleteLocalRef(cls);
    }
}
static Pos position(jint x, jint y) {
    if (x < 0 || x > 65535 || y < 0 || y > 65535) throw std::invalid_argument("Invalid tile coordinates");
    return Pos(x, y);
}
static Rect rectangle(jint x1, jint y1, jint x2, jint y2) {
    if (x2 == -1) x2 = 65535;
    if (y2 == -1) y2 = 65535;
    auto p1 = position(x1, y1), p2 = position(x2, y2);
    if (p1.x > p2.x || p1.y > p2.y) throw std::invalid_argument("Invalid rectangle");
    return Rect(p1.x, p1.y, p2.x, p2.y);
}
static std::filesystem::path archivePath(const std::string& input) {
    if (input.empty()) return {};
    std::filesystem::path path(input);
    if (path.is_relative()) {
        path = path.lexically_normal();
        if (*path.begin() == "..") throw std::invalid_argument("Archive path escapes tilelogs");
        path = std::filesystem::path("config/tilelogs") / path;
    }
    return path.replace_extension("history");
}

static jobjectArray marshal(JNIEnv* env, const std::vector<TileState>& states, bool preview = false) {
    jclass cls = env->FindClass("tilelogger/TileState");
    if (!cls) return nullptr;
#define FIELD(name, signature)                              \
    jfieldID name = env->GetFieldID(cls, #name, signature); \
    if (!name) return nullptr
    FIELD(x, "I");
    FIELD(y, "I");
    FIELD(uuid, "Ljava/lang/String;");
    FIELD(team, "I");
    FIELD(valid, "Z");
    FIELD(time, "J");
    FIELD(block, "I");
    FIELD(destroy, "Z");
    FIELD(rotation, "I");
    FIELD(config_type, "I");
    FIELD(config, "Ljava/lang/Object;");
    FIELD(action_kind, "I");
    FIELD(sequence, "J");
    FIELD(planId, "J");
    FIELD(expectedSequence, "J");
    FIELD(legacy, "Z");
    FIELD(configBytes, "I");
    FIELD(configTruncated, "Z");
#undef FIELD
    jclass integer = env->FindClass("java/lang/Integer");
    if (!integer) return nullptr;
    jmethodID valueOf = env->GetStaticMethodID(integer, "valueOf", "(I)Ljava/lang/Integer;");
    if (!valueOf) return nullptr;
    auto array = env->NewObjectArray(static_cast<jsize>(states.size()), cls, nullptr);
    if (!array) return nullptr;
    for (size_t i = 0; i < states.size(); ++i) {
        if (env->PushLocalFrame(8) < 0) return nullptr;
        const auto& s = states[i];
        jobject object = env->AllocObject(cls);
        if (!object) {
            env->PopLocalFrame(nullptr);
            return nullptr;
        }
        env->SetIntField(object, x, s.pos.x);
        env->SetIntField(object, y, s.pos.y);
        env->SetIntField(object, team, s.team);
        env->SetIntField(object, block, s.block);
        env->SetBooleanField(object, valid, s.valid);
        env->SetBooleanField(object, destroy, s.destroy);
        env->SetIntField(object, rotation, s.rotation);
        env->SetIntField(object, config_type, s.config_type);
        env->SetIntField(object, action_kind, static_cast<int>(s.action));
        env->SetLongField(object, time, s.time);
        env->SetLongField(object, sequence, s.sequence);
        env->SetLongField(object, planId, s.plan);
        env->SetLongField(object, expectedSequence, s.expected);
        env->SetBooleanField(object, legacy, active->Legacy());
        const auto& player = active->GetPlayer(s.player);
        jstring actor = env->NewStringUTF(std::string(player.begin(), player.end()).c_str());
        if (!actor) {
            env->PopLocalFrame(nullptr);
            return nullptr;
        }
        env->SetObjectField(object, uuid, actor);
        if (s.config_type >= 5) {
            const auto& bytes = active->GetConfig(s.config);
            size_t length = bytes.size();
            if (preview) {
                size_t limit = s.config_type == 7 ? 64 : 1028;
                length = std::min(length, limit);
            }
            env->SetIntField(object, configBytes, static_cast<jint>(bytes.size()));
            env->SetBooleanField(object, configTruncated, length < bytes.size());
            auto payload = env->NewByteArray(static_cast<jsize>(length));
            if (!payload) {
                env->PopLocalFrame(nullptr);
                return nullptr;
            }
            if (length)
                env->SetByteArrayRegion(payload, 0, static_cast<jsize>(length),
                                        reinterpret_cast<const jbyte*>(bytes.data()));
            if (env->ExceptionCheck()) {
                env->PopLocalFrame(nullptr);
                return nullptr;
            }
            env->SetObjectField(object, config, payload);
        } else {
            auto boxed = env->CallStaticObjectMethod(integer, valueOf, static_cast<jint>(s.config));
            if (env->ExceptionCheck() || !boxed) {
                env->PopLocalFrame(nullptr);
                return nullptr;
            }
            env->SetObjectField(object, config, boxed);
        }
        env->SetObjectArrayElement(array, static_cast<jsize>(i), object);
        if (env->ExceptionCheck()) {
            env->PopLocalFrame(nullptr);
            return nullptr;
        }
        env->PopLocalFrame(nullptr);
    }
    return array;
}

JNIEXPORT jint JNICALL Java_tilelogger_TileLogger_abi(JNIEnv*, jclass) { return 2; }
JNIEXPORT void JNICALL Java_tilelogger_TileLogger_start(JNIEnv* env, jclass, jstring path, jstring session,
                                                        jstring manifest) {
    try {
        auto candidate = std::make_unique<TileLogger>(writer);
        candidate->Start(archivePath(UtfChars(env, path).string()), UtfChars(env, session).string(),
                         UtfChars(env, manifest).string());
        active = std::move(candidate);
    } catch (const std::exception& e) {
        fail(env, e);
    }
}
JNIEXPORT jlong JNICALL Java_tilelogger_TileLogger_prepare(JNIEnv* env, jclass, jstring path,
                                                           jstring manifest) {
    try {
        auto resolved = archivePath(UtfChars(env, path).string());
        if (resolved.empty()) throw std::invalid_argument("Archive path required");
        writer.Flush();
        auto candidate = std::make_unique<TileLogger>(writer);
        candidate->Load(resolved);
        if (!candidate->Legacy() && candidate->Manifest() != UtfChars(env, manifest).string())
            throw std::invalid_argument("Archive content manifest differs from the current server");
        std::lock_guard lock(prepared_mutex);
        if (prepared.size() >= 4) throw std::runtime_error("Too many pending archive readers");
        auto handle = ++next_handle;
        prepared.emplace(handle, std::move(candidate));
        return handle;
    } catch (const std::exception& e) {
        fail(env, e);
        return 0;
    }
}
JNIEXPORT void JNICALL Java_tilelogger_TileLogger_activate(JNIEnv* env, jclass, jlong handle) {
    try {
        std::lock_guard lock(prepared_mutex);
        auto found = prepared.find(handle);
        if (found == prepared.end()) throw std::invalid_argument("Unknown archive handle");
        active = std::move(found->second);
        prepared.erase(found);
    } catch (const std::exception& e) {
        fail(env, e);
    }
}
JNIEXPORT void JNICALL Java_tilelogger_TileLogger_discard(JNIEnv*, jclass, jlong handle) {
    std::lock_guard lock(prepared_mutex);
    prepared.erase(handle);
}
JNIEXPORT jlong JNICALL Java_tilelogger_TileLogger_elapsed(JNIEnv*, jclass) {
    return static_cast<jlong>(active->Duration());
}
JNIEXPORT void JNICALL Java_tilelogger_TileLogger_action(JNIEnv* env, jclass, jint x, jint y, jstring uuid,
                                                         jint team, jint block, jint rotation, jint type,
                                                         jint scalar, jbyteArray payload, jint kind) {
    try {
        if (!payload) throw std::invalid_argument("Null configuration bytes");
        auto length = env->GetArrayLength(payload);
        if (length > static_cast<jsize>(max_config_bytes))
            throw std::invalid_argument("Configuration exceeds 1 MiB");
        DataVec data(length);
        if (length) env->GetByteArrayRegion(payload, 0, length, reinterpret_cast<jbyte*>(data.data()));
        if (env->ExceptionCheck()) return;
        active->Record(position(x, y), UtfChars(env, uuid).bytes(), team, block, rotation, type,
                       static_cast<uint32_t>(scalar), data, static_cast<ActionKind>(kind));
    } catch (const std::exception& e) {
        fail(env, e);
    }
}
JNIEXPORT jobjectArray JNICALL Java_tilelogger_TileLogger_history(JNIEnv* env, jclass, jint x1, jint y1,
                                                                  jint x2, jint y2, jstring uuid, jint teams,
                                                                  jlong time, jlong size, jlong before) {
    try {
        if (size < 1 || size > 4097 || before < 1)
            throw std::invalid_argument("Invalid history limit/cursor");
        return marshal(env,
                       active->GetHistory(rectangle(x1, y1, x2, y2), UtfChars(env, uuid).bytes(), teams, time,
                                          size, before),
                       true);
    } catch (const std::exception& e) {
        fail(env, e);
        return nullptr;
    }
}
JNIEXPORT jobjectArray JNICALL Java_tilelogger_TileLogger_plan(JNIEnv* env, jclass, jint x1, jint y1, jint x2,
                                                               jint y2, jstring uuid, jint teams,
                                                               jlong time) {
    try {
        return marshal(
            env, active->PlanRollback(rectangle(x1, y1, x2, y2), UtfChars(env, uuid).bytes(), teams, time));
    } catch (const std::exception& e) {
        fail(env, e);
        return nullptr;
    }
}
JNIEXPORT void JNICALL Java_tilelogger_TileLogger_commit(JNIEnv* env, jclass, jlong plan, jint x, jint y) {
    try {
        active->Commit(plan, position(x, y));
    } catch (const std::exception& e) {
        fail(env, e);
    }
}
JNIEXPORT void JNICALL Java_tilelogger_TileLogger_forget(JNIEnv*, jclass, jlong plan) {
    active->Discard(plan);
}
JNIEXPORT jlong JNICALL Java_tilelogger_TileLogger_memory(JNIEnv* env, jclass, jint id) {
    try {
        return active->MemoryUsage(id);
    } catch (const std::exception& e) {
        fail(env, e);
        return 0;
    }
}
JNIEXPORT jboolean JNICALL Java_tilelogger_TileLogger_readOnly(JNIEnv*, jclass) { return active->ReadOnly(); }
JNIEXPORT jstring JNICALL Java_tilelogger_TileLogger_session(JNIEnv* env, jclass) {
    return env->NewStringUTF(active->Session().c_str());
}
JNIEXPORT jstring JNICALL Java_tilelogger_TileLogger_health(JNIEnv* env, jclass) {
    return env->NewStringUTF(active->Status().c_str());
}
JNIEXPORT void JNICALL Java_tilelogger_TileLogger_writable(JNIEnv* env, jclass) {
    try {
        active->CheckWritable();
    } catch (const std::exception& e) {
        fail(env, e);
    }
}
JNIEXPORT jstring JNICALL Java_tilelogger_TileLogger_build(JNIEnv* env, jclass) {
    return env->NewStringUTF("v2 " __DATE__ " " __TIME__);
}
JNIEXPORT void JNICALL Java_tilelogger_TileLogger_flushWrites(JNIEnv* env, jclass) {
    try {
        writer.Flush();
    } catch (const std::exception& e) {
        fail(env, e);
    }
}

JNIEXPORT jobjectArray JNICALL Java_tilelogger_TileLogger_current(JNIEnv* env, jclass, jint x, jint y) {
    try {
        auto value = active->Current(position(x, y));
        return marshal(env, value ? std::vector<TileState>{*value} : std::vector<TileState>{});
    } catch (const std::exception& e) {
        fail(env, e);
        return nullptr;
    }
}
