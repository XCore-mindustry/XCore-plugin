#pragma once
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <deque>
#include <filesystem>
#include <future>
#include <memory>
#include <mutex>
#include <thread>
#include "Binary.h"
#ifdef _WIN32
#include <io.h>
#else
#include <unistd.h>
#include <fcntl.h>
#endif

struct JournalState {
    std::filesystem::path path;
    std::atomic<uint64_t> accepted{}, durable{};
    std::atomic<size_t> queued{};
    std::atomic<bool> failed{}, write_failed{};
    mutable std::mutex mutex;
    std::string error;
    void Check() const {
        if (failed) {
            std::lock_guard lock(mutex);
            throw std::runtime_error(error);
        }
    }
    void Fail(const std::string& message, bool writerError = false) {
        std::lock_guard lock(mutex);
        if (!failed) error = message;
        failed = true;
        if (writerError) write_failed = true;
    }
};

// Only this worker owns files. Game-thread callers enqueue immutable byte copies.
class JournalWriter {
    struct Job {
        std::shared_ptr<JournalState> state;
        DataVec bytes;
        bool open{};
        std::shared_ptr<std::promise<void>> barrier;
    };

public:
    JournalWriter(size_t max_bytes = 32 * 1024 * 1024, size_t max_jobs = 4096)
        : max_bytes_(max_bytes), max_jobs_(max_jobs), worker_([this] { Run(); }) {}
    ~JournalWriter() { Stop(); }
    JournalWriter(const JournalWriter&) = delete;
    void Enqueue(std::shared_ptr<JournalState> state, DataVec bytes, bool open = false) {
        state->Check();
        std::lock_guard lock(mutex_);
        if (stopping_) throw std::runtime_error("Journal writer stopped");
        if (bytes.size() > max_bytes_ || queued_bytes_ > max_bytes_ - bytes.size() ||
            jobs_.size() >= max_jobs_) {
            state->Fail("Journal queue full; recording suspended");
            throw std::runtime_error("Journal queue full; recording suspended");
        }
        auto length = bytes.size();
        jobs_.push_back({state, std::move(bytes), open, {}});
        queued_bytes_ += length;
        state->queued += length;
        ++state->accepted;
        changed_.notify_one();
    }
    // Drain/sync only; session health remains available through JournalState::Check().
    // Background archive readers and shutdown only; never call from a game tick.
    void Flush() {
        auto promise = std::make_shared<std::promise<void>>();
        auto done = promise->get_future();
        {
            std::lock_guard lock(mutex_);
            if (stopping_) throw std::runtime_error("Journal writer stopped");
            jobs_.push_back({{}, {}, false, promise});
            changed_.notify_one();
        }
        done.get();
    }
    void Stop() {
        {
            std::lock_guard lock(mutex_);
            stopping_ = true;
            changed_.notify_one();
        }
        if (worker_.joinable()) worker_.join();
    }

private:
    void Run() noexcept {
        FILE* file = nullptr;
        std::shared_ptr<JournalState> current;
        uint64_t written = 0;
        bool new_entry = false;
        auto last_sync = std::chrono::steady_clock::now();
        auto sync = [&] {
            if (!file) return;
            if (std::fflush(file) != 0) throw std::runtime_error("Cannot flush journal");
#ifdef _WIN32
            if (_commit(_fileno(file)) != 0) throw std::runtime_error("Cannot sync journal");
#else
            if (::fsync(fileno(file)) != 0) throw std::runtime_error("Cannot sync journal");
            if (new_entry) {
                int directory = ::open(current->path.parent_path().c_str(), O_RDONLY | O_DIRECTORY);
                if (directory < 0) throw std::runtime_error("Cannot open journal directory");
                int result = ::fsync(directory);
                ::close(directory);
                if (result != 0) throw std::runtime_error("Cannot sync journal directory");
            }
#endif
            new_entry = false;
            current->durable = written;
            last_sync = std::chrono::steady_clock::now();
        };
        auto close = [&] {
            if (!file) return;
            FILE* closing = file;
            file = nullptr;
            if (std::fclose(closing) != 0) throw std::runtime_error("Cannot close journal");
        };
        for (;;) {
            Job job;
            bool have_job = false;
            {
                std::unique_lock lock(mutex_);
                changed_.wait_for(lock, std::chrono::milliseconds(250),
                                  [&] { return stopping_ || !jobs_.empty(); });
                if (!jobs_.empty()) {
                    job = std::move(jobs_.front());
                    jobs_.pop_front();
                    queued_bytes_ -= job.bytes.size();
                    have_job = true;
                } else if (stopping_)
                    break;
            }
            try {
                if (have_job && job.barrier) {
                    sync();
                    close();
                    job.barrier->set_value();
                    continue;
                }
                if (have_job) {
                    job.state->queued -= job.bytes.size();
                    if (job.open) {
                        sync();
                        close();
                        current = job.state;
                        written = 0;
                        new_entry = true;
                        std::filesystem::create_directories(current->path.parent_path());
                        // Exclusive creation: never truncate an existing archive.
                        file = std::fopen(current->path.string().c_str(), "wbx");
                        if (!file)
                            throw std::runtime_error("Cannot create new journal: " + current->path.string());
                    }
                    if (job.state->write_failed) throw std::runtime_error("Journal writer failed");
                    if (current != job.state) throw std::runtime_error("Journal session mismatch");
                    if (!file) {
                        file = std::fopen(current->path.string().c_str(), "r+b");
                        if (!file || std::fseek(file, 0, SEEK_END) != 0)
                            throw std::runtime_error("Cannot reopen journal");
                    }
                    if (std::fwrite(job.bytes.data(), 1, job.bytes.size(), file) != job.bytes.size())
                        throw std::runtime_error("Cannot append journal");
                    ++written;
                }
                if (std::chrono::steady_clock::now() - last_sync >= std::chrono::milliseconds(250)) sync();
            } catch (const std::exception& e) {
                if (have_job && job.state)
                    job.state->Fail(e.what(), true);
                else if (current)
                    current->Fail(e.what(), true);
                if (job.barrier) job.barrier->set_exception(std::current_exception());
            }
        }
        try {
            sync();
            close();
        } catch (const std::exception& e) {
            if (current) current->Fail(e.what(), true);
        }
        if (file) std::fclose(file);
    }
    std::mutex mutex_;
    std::condition_variable changed_;
    std::deque<Job> jobs_;
    size_t queued_bytes_{};
    const size_t max_bytes_, max_jobs_;
    bool stopping_{};
    std::thread worker_;
};
