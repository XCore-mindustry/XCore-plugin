#pragma once
#include <string>
#include <chrono>

#include "HistoryStack.h"
#include "SubnetFilter.h"
#include "Cache.h"

namespace std {
    template<>
    struct hash<DataVec> {
        size_t operator()(const DataVec& vec) const noexcept {
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
    timestamp_t_ Reset(std::filesystem::path path, bool write) {
        if (path.is_relative()) {
            path = tilelogs_ / path;
            std::filesystem::create_directories(tilelogs_);
        }
        time_t_ duration = history_.Reset(path.replace_extension("history"), write);
        players_.Reset(path.replace_extension("players"), write);
        configs_.Reset(path.replace_extension("configs"), write);
        subnet_filter_.reset(subnets_ / "accept.txt", subnets_ / "deny.txt");
        time_begin_ = std::chrono::steady_clock::now() - std::chrono::seconds(duration);
        return std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now().time_since_epoch()).count();
    }

    time_t_ Duration() const {
        std::chrono::nanoseconds duration = std::chrono::steady_clock::now() - time_begin_;
        return std::chrono::duration_cast<std::chrono::duration<time_t_>>(duration).count();
    }

    const DataVec& GetPlayer(player_t_ player) const {
        return players_[player];
    }

    const DataVec& GetConfig(config_t_ config) const {
        return configs_[config];
    }

    template<class T>
    void Record(const Pos& pos, const DataVec& uuid, team_t_ team, block_t_ block, rotation_t_ rotation, config_type_t_ config_type, const T& config) {
        config_t_ config_id;
        if constexpr (std::is_same_v<T, DataVec>)
            config_id = configs_[config];
        else
            config_id = config;
        history_.Record(TileState(pos, players_[uuid], team, 1, Duration(), block, !block, rotation, config_type, config_id));
    }

    std::vector<TileState> GetHistory(const Rect& rect, const DataVec& uuid, int teams, int time, size_t size) const {
        std::optional<player_t_> player = uuid.empty() ? std::nullopt : players_.at(uuid);
        if (!player && uuid.size())
            return {}; // uuid not found
        return history_.Last(rect, player, teams, AbsTime(time), size);
    }

    std::vector<TileState> Rollback(const Rect& rect, const DataVec& uuid, int teams, int time, HistoryStack::RollbackFlags flags) {
        std::optional<player_t_> player = uuid.empty() ? std::nullopt : players_.at(uuid);
        if (!player && uuid.size())
            return {}; // uuid not found
        return history_.Rollback(rect, player, teams, AbsTime(time), flags);
    }

    bool SubnetAccepted(const std::string& subnet) {
        return subnet_filter_.accepted(subnet);
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

    static inline const std::filesystem::path tilelogs_ = "config/tilelogs";
    static inline const std::filesystem::path subnets_ = "config/subnets";

    std::chrono::steady_clock::time_point time_begin_{};
    HistoryStack history_;
    SubnetFilter subnet_filter_;
    Cache<player_t_> players_;
    Cache<config_t_> configs_;
};
