#pragma once

#pragma pack(push, 2)
struct TileState {
    uint16_t player;
    uint16_t time;
    uint16_t block : 11;
    uint16_t rotation : 2;
    uint16_t config_type : 3;
    uint32_t config;

    TileState() : player(), time(), block(), rotation(), config() {}
    TileState(uint16_t player, uint16_t time, uint16_t block, uint16_t rotation, uint16_t config_type, uint32_t config)
        : player(player), time(time), block(block), rotation(rotation), config_type(config_type), config(config) {}

    bool operator==(const TileState& o) const = delete;
    bool BlockRotationConfigEquals(const TileState& o) const {
        return block == o.block && rotation == o.rotation && config_type == o.config_type && config == o.config;
    }
};

struct TileStateXY : public TileState {
    uint16_t x;
    uint16_t y;

    TileStateXY(const TileState& tile_state, uint16_t x, uint16_t y)
        : TileState(tile_state), x(x), y(y) {}
};

#pragma pack(pop)

static_assert(sizeof TileState == 10);

#define TILESTATE_H_GEN_TYPE_ALIAS(name) \
    using name##_t_ = decltype(TileState::name);
TILESTATE_H_GEN_TYPE_ALIAS(player);
TILESTATE_H_GEN_TYPE_ALIAS(time);
TILESTATE_H_GEN_TYPE_ALIAS(block);
TILESTATE_H_GEN_TYPE_ALIAS(rotation);
TILESTATE_H_GEN_TYPE_ALIAS(config_type);
TILESTATE_H_GEN_TYPE_ALIAS(config);
#undef TILESTATE_H_GEN_TYPE_ALIAS

using pos_t_ = decltype(TileStateXY::x);
using timestamp_t_ = uint64_t;