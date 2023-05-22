#pragma once
#include "BitStack.h"
#include <cstdint>

enum class Serialize {
    read,
    write,
};

#pragma pack(push, 2)
struct Pos {
    uint16_t x;
    uint16_t y;

    Pos() = default;
    Pos(auto x, auto y) : x(x), y(y) {}
    bool operator==(const Pos& o) const = default;
};

struct Rect {
    Pos p1, p2;

    Rect() = default;
    Rect(auto x1, auto y1, auto x2, auto y2) : p1(x1,y1), p2(x2,y2) {}
    bool contains(const Pos& pos) const {
        return pos.x >= p1.x && pos.x <= p2.x && pos.y >= p1.y && pos.y <= p2.y;
    }
};

struct TileState {
    Pos pos{};
    uint16_t player : 13{};
    uint16_t team : 3{};
    uint16_t time{};
    uint16_t valid : 1{};
    uint16_t block : 10{};
    uint16_t rotation : 2{};
    uint16_t config_type : 3{};
    uint32_t config{};

    TileState() = default;
    TileState(const Pos& pos) : pos(pos) {}
    TileState(const Pos& pos, uint16_t player, uint16_t team, uint16_t time, uint16_t valid, uint16_t block, uint16_t rotation, uint16_t config_type, uint32_t config)
        : pos(pos), player(player), team(team), time(time), valid(valid), block(block), rotation(rotation), config_type(config_type), config(config) {}

    bool operator==(const TileState& o) const = delete;
    bool BlockEquals(const TileState& o) const {
        return team == o.team && block == o.block && rotation == o.rotation && config_type == o.config_type && config == o.config;
    }

    void Serialize(BitStack& bs, Serialize mode) {
#define TILESTATE_H_BITSTREAM(f) \
        if (mode == Serialize::write) \
            bs.push(f, GET_BIT_FIELD_WIDTH(TileState, f)); \
        else \
            f = static_cast<decltype(f)>(bs.read(GET_BIT_FIELD_WIDTH(TileState, f)))
        TILESTATE_H_BITSTREAM(pos.x);
        TILESTATE_H_BITSTREAM(pos.y);
        TILESTATE_H_BITSTREAM(player);
        TILESTATE_H_BITSTREAM(team);
        TILESTATE_H_BITSTREAM(time);
        TILESTATE_H_BITSTREAM(valid);
        TILESTATE_H_BITSTREAM(block);
        TILESTATE_H_BITSTREAM(rotation);
        TILESTATE_H_BITSTREAM(config_type);
        TILESTATE_H_BITSTREAM(config);
#undef TILESTATE_H_BITSTREAM
    }
};
#pragma pack(pop)

static_assert(sizeof(TileState) == 14);

namespace std {
    template<>
    struct hash<Pos> {
        size_t operator()(const Pos& pos) const noexcept {
            return pos.x << 16 | pos.y;
        }
    };
}

#define TILESTATE_H_GEN_TYPE_ALIAS(name) \
    using name##_t_ = decltype(TileState::name);
TILESTATE_H_GEN_TYPE_ALIAS(player);
TILESTATE_H_GEN_TYPE_ALIAS(team);
TILESTATE_H_GEN_TYPE_ALIAS(time);
TILESTATE_H_GEN_TYPE_ALIAS(valid);
TILESTATE_H_GEN_TYPE_ALIAS(block);
TILESTATE_H_GEN_TYPE_ALIAS(rotation);
TILESTATE_H_GEN_TYPE_ALIAS(config_type);
TILESTATE_H_GEN_TYPE_ALIAS(config);
#undef TILESTATE_H_GEN_TYPE_ALIAS

using timestamp_t_ = uint64_t;
using stack_counter_t_ = uint32_t;