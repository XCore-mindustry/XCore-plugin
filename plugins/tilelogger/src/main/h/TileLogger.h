#pragma once
#include <string>
#include <chrono>

#include "HistoryStack.h"
#include "Cache.h"

using ConfigData = std::vector<std::byte>;

namespace std {
    template<>
    struct hash<ConfigData> {
        size_t operator()(const ConfigData& vec) const noexcept {
            size_t hash = 0xcbf29ce484222325;
            for (const auto& data : vec) {
                hash ^= static_cast<size_t>(data);
                hash *= 0x00000100000001B3;
            }
            return hash;
        }
    };
}

class TileLogger {
public:
    timestamp_t_ Reset(Pos sizes) {
        sizes_ = sizes;
        history_ = {};
        players_ = {};
        configs_ = {};
        time_begin_ = std::chrono::steady_clock::now();
        return std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now().time_since_epoch()).count();
    }

    time_t_ Duration() const {
        std::chrono::nanoseconds duration = std::chrono::steady_clock::now() - time_begin_;
        return std::chrono::duration_cast<std::chrono::duration<time_t_>>(duration).count();
    }

    const std::string& GetPlayer(player_t_ player) const {
        return players_[player];
    }

    const ConfigData& GetConfig(config_t_ config) const {
        return configs_[config];
    }

    template<class T>
    void Record(const Pos& pos, const std::string& uuid, team_t_ team, block_t_ block, rotation_t_ rotation, config_type_t_ config_type, const T& config) {
        config_t_ config_id;
        if constexpr (std::is_same_v<T, ConfigData>)
            config_id = configs_[config];
        else
            config_id = config;
        history_.Record(TileState(pos, players_[uuid], team, Duration(), 1, block, rotation, config_type, config_id));
    }

    std::vector<TileState> GetHistory(const Rect& rect, const std::string& uuid, int teams, int time, size_t size) const {
        std::optional<player_t_> player = uuid.empty() ? std::nullopt : players_.at(uuid);
        if (!player && uuid.size())
            return {}; // uuid not found
        return history_.Last(rect, player, teams, AbsTime(time), size);
    }

    std::vector<TileState> Rollback(const Rect& rect, const std::string& uuid, int teams, int time, HistoryStack::RollbackFlags flags) {
        std::optional<player_t_> player = uuid.empty() ? std::nullopt : players_.at(uuid);
        if (!player && uuid.size())
            return {}; // uuid not found
        return history_.Rollback(rect, player, teams, AbsTime(time), flags);
    }

    size_t MemoryUsage(size_t id) const {
        switch (id) {
        case 2:
            return history_.Size();
        case 3:
            return history_.Capacity();
        case 4:
            return players_.Size();
        case 5:
            return players_.Capacity();
        case 6:
            return configs_.Size();
        case 7:
            return configs_.Capacity();
        default:
            return 0;
        }
    }

private:
    time_t_ AbsTime(int time) const {
        if (time < 0)
            time += Duration();
        return static_cast<time_t_>(std::clamp(time, 0, 0xffff));
    }

    Pos sizes_;
    std::chrono::steady_clock::time_point time_begin_{};

    HistoryStack history_;
    Cache<std::string, player_t_> players_;
    Cache<ConfigData, config_t_> configs_;
};
