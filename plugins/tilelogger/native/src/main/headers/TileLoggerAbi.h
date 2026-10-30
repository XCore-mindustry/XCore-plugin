#pragma once
#include <stdint.h>

#ifdef _WIN32
#define TL_API __declspec(dllexport)
#else
#define TL_API __attribute__((visibility("default")))
#endif
#ifdef __cplusplus
#define TL_NOEXCEPT noexcept
extern "C" {
#else
#define TL_NOEXCEPT
#endif

// ABI 3 targets 64-bit hosts. Caller owns this 32-byte descriptor; tl_release owns its buffer.
// Status: 0 success, 1 invalid argument, 2 state/I/O error, 3 allocation failure.
// On errors data/size contain UTF-8 diagnostics, except allocation failure may have no diagnostic.
// Release a result before reusing its descriptor; release also clears it and is safe to repeat.
// Row buffers: u32 count; each row has x/y/team/block:u16, rotation/type/action/flags:u8,
// time/sequence/plan/expected:u64, scalar/full-config-bytes:u32, then UUID and config blobs
// (u32 length + bytes). Integers in buffers are little-endian. Flags: valid=1, destroy=2,
// legacy=4, config-truncated=8. Buffer ownership never depends on an active session.
// Active-store calls are single-owner; prepare/discard/flush can run on workers.
typedef struct TLResult {
    uint64_t value;
    const uint8_t* data;
    uint64_t size;
    void* owner;
} TLResult;

TL_API int32_t tl_abi_version(void) TL_NOEXCEPT;
TL_API void tl_release(TLResult*) TL_NOEXCEPT;
TL_API int32_t tl_start(TLResult*, const char* path, const char* session, const char* manifest) TL_NOEXCEPT;
TL_API int32_t tl_prepare(TLResult*, const char* path, const char* manifest) TL_NOEXCEPT;
TL_API int32_t tl_activate(TLResult*, uint64_t handle) TL_NOEXCEPT;
TL_API int32_t tl_discard(TLResult*, uint64_t handle) TL_NOEXCEPT;
TL_API int32_t tl_elapsed(TLResult*) TL_NOEXCEPT;
// All input pointers are borrowed only for this call. The writer queues owned copies.
TL_API int32_t tl_record(TLResult*, int32_t x, int32_t y, const uint8_t* uuid, uint64_t uuid_size,
                        int32_t team, int32_t block, int32_t rotation, int32_t type, int32_t scalar,
                        const uint8_t* payload, uint64_t payload_size, int32_t kind) TL_NOEXCEPT;
TL_API int32_t tl_history(TLResult*, int32_t x1, int32_t y1, int32_t x2, int32_t y2,
                         const uint8_t* uuid, uint64_t uuid_size, int32_t teams, int64_t time,
                         int64_t size, int64_t before) TL_NOEXCEPT;
TL_API int32_t tl_plan(TLResult*, int32_t x1, int32_t y1, int32_t x2, int32_t y2,
                      const uint8_t* uuid, uint64_t uuid_size, int32_t teams, int64_t time) TL_NOEXCEPT;
TL_API int32_t tl_commit(TLResult*, uint64_t plan, int32_t x, int32_t y) TL_NOEXCEPT;
TL_API int32_t tl_forget(TLResult*, uint64_t plan) TL_NOEXCEPT;
TL_API int32_t tl_current(TLResult*, int32_t x, int32_t y) TL_NOEXCEPT;
TL_API int32_t tl_memory(TLResult*, int32_t id) TL_NOEXCEPT;
TL_API int32_t tl_read_only(TLResult*) TL_NOEXCEPT;
TL_API int32_t tl_session(TLResult*) TL_NOEXCEPT;
TL_API int32_t tl_status(TLResult*) TL_NOEXCEPT;
TL_API int32_t tl_writable(TLResult*) TL_NOEXCEPT;
TL_API int32_t tl_build(TLResult*) TL_NOEXCEPT;
TL_API int32_t tl_flush(TLResult*) TL_NOEXCEPT;

#ifdef __cplusplus
}
#endif
