#pragma once
#include <cstdint>
#include <functional>

using time_t_ = uint64_t;
using player_t_ = uint32_t;
using config_t_ = uint32_t;
using timestamp_t_ = uint64_t;

enum class ActionKind : uint8_t {
    Baseline,
    BuildStarted,
    BuildCompleted,
    DestroyStarted,
    DestroyCompleted,
    Rotate,
    Configure,
    Pickup,
    Drop,
    AdminFill,
    Legacy = 255
};

struct Pos {
    uint16_t x{}, y{};
    Pos() = default;
    Pos(auto x_, auto y_) : x(static_cast<uint16_t>(x_)), y(static_cast<uint16_t>(y_)) {}
    bool operator==(const Pos&) const = default;
};

struct Rect {
    Pos p1, p2;
    Rect() = default;
    Rect(auto x1, auto y1, auto x2, auto y2) : p1(x1, y1), p2(x2, y2) {}
    bool contains(const Pos& p) const { return p.x >= p1.x && p.x <= p2.x && p.y >= p1.y && p.y <= p2.y; }
};

struct TileState {
    Pos pos{};
    player_t_ player{};
    uint16_t team{};
    bool valid{};
    time_t_ time{};
    uint16_t block{};
    bool destroy{};
    uint8_t rotation{}, config_type{};
    config_t_ config{};
    ActionKind action{ActionKind::Legacy};
    uint64_t sequence{}, plan{}, expected{};

    TileState() = default;
    explicit TileState(const Pos& p) : pos(p) {}
    TileState(const Pos& p, player_t_ player_, uint16_t team_, bool valid_, time_t_ time_, uint16_t block_,
              bool destroy_, uint8_t rotation_, uint8_t type_, config_t_ config_)
        : pos(p),
          player(player_),
          team(team_),
          valid(valid_),
          time(time_),
          block(block_),
          destroy(destroy_),
          rotation(rotation_),
          config_type(type_),
          config(config_) {}
    bool isSnapshot() const {
        return action != ActionKind::BuildStarted && action != ActionKind::DestroyStarted;
    }
    bool RecordAction(const TileState& o) const {
        return team != o.team || block != o.block || destroy != o.destroy || rotation != o.rotation ||
               config_type != o.config_type || config != o.config;
    }
};

namespace std {
template <>
struct hash<Pos> {
    size_t operator()(const Pos& p) const noexcept { return (static_cast<uint32_t>(p.x) << 16) | p.y; }
};
}  // namespace std
