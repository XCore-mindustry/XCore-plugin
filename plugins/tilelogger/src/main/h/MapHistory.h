#pragma once
#include <string>
#include <chrono>

#include "TileHistory.h"
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

class MapHistory {
public:
    timestamp_t_ Reset(pos_t_ w, pos_t_ h) {
        width_ = w;
        height_ = h;
        tiles_.clear();
        tiles_.resize(w*h);
        players_.Reset();
        configs_.Reset();
        time_begin_ = std::chrono::steady_clock::now();
        return std::chrono::duration_cast<std::chrono::seconds>(std::chrono::system_clock::now().time_since_epoch()).count();
    }

    const TileHistory& Tile(pos_t_ x, pos_t_ y) const {
        return tiles_[y*width_ + x];
    }

    TileHistory& Tile(pos_t_ x, pos_t_ y) {
        return const_cast<TileHistory&>(const_cast<const MapHistory*>(this)->Tile(x, y));
    }

    time_t_ Duration() const {
        std::chrono::nanoseconds duration = std::chrono::steady_clock::now() - time_begin_;
        return std::chrono::duration_cast<std::chrono::duration<time_t_>>(duration).count();
    }

    const std::string& CachePlayer(player_t_ player) const {
        return players_[player];
    }

    const ConfigData& CacheConfig(config_t_ config) const {
        return configs_[config];
    }

    template<class T>
    void Record(pos_t_ x, pos_t_ y, const std::string& uuid, block_t_ block, rotation_t_ rotation, config_type_t_ config_type, const T& config) {
        config_t_ config_id;
        if constexpr (std::is_same_v<T, ConfigData>)
            config_id = configs_[config];
        else
            config_id = config;
        Tile(x, y).Record(TileState(players_[uuid], Duration(), block, rotation, config_type, config_id));
    }

    void ClampPos(pos_t_& x, pos_t_& y) const {
        x = std::clamp(x, pos_t_{}, static_cast<pos_t_>(width_ - 1));
        y = std::clamp(y, pos_t_{}, static_cast<pos_t_>(height_ - 1));
    }

    std::vector<TileState> GetHistory(pos_t_ x, pos_t_ y, size_t size) const {
        ClampPos(x, y);
        return Tile(x, y).Last(size);
    }

    std::vector<TileStateXY> Rollback(pos_t_ x1, pos_t_ y1, pos_t_ x2, pos_t_ y2, const std::string& uuid, int time, bool erase) {
        ClampPos(x1, y1);
        ClampPos(x2, y2);
        std::vector<TileStateXY> states_remove;
        std::vector<TileStateXY> states_add;
        std::optional<player_t_> player;
        if (!uuid.empty())
            player = players_[uuid];
        if (time < 0)
            time += Duration();
        time = std::clamp(time, 0, 0xffff);
        for (pos_t_ x = x1; x <= x2; x++) {
            for (pos_t_ y = y1; y <= y2; y++) {
                if (const std::optional<TileState>& state = Tile(x, y).Rollback(player, static_cast<time_t_>(time), erase))
                    (state->block ? states_add : states_remove).emplace_back(*state, x, y);
            }
        }
        states_remove.insert(states_remove.end(), states_add.begin(), states_add.end());
        return states_remove;
    }

    size_t MemoryUsage(size_t id) const {
        switch (id) {
        case 0:
            return tiles_.size() * sizeof(tiles_[0]);
        case 1:
            return tiles_.capacity() * sizeof(tiles_[0]);
        case 2:
            return std::accumulate(tiles_.begin(), tiles_.end(), 0ull, [](size_t a, const TileHistory& b){
                    return a + b.Size();
                });
        case 3:
            return std::accumulate(tiles_.begin(), tiles_.end(), 0ull, [](size_t a, const TileHistory& b){
                    return a + b.Capacity();
                });
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
    pos_t_ width_;
    pos_t_ height_;
    std::chrono::steady_clock::time_point time_begin_{};

    std::vector<TileHistory> tiles_;
    Cache<std::string, player_t_> players_;
    Cache<ConfigData, config_t_> configs_;
};
