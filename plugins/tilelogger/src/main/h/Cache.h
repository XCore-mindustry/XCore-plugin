#pragma once
#include <vector>
#include <unordered_map>

template<class Data, class Id>
class Cache {
public:
    void Reset() {
        ids_.clear();
        data_.clear();
    }

    Id operator[](const Data& data) {
        auto [it, insert] = ids_.insert({data, static_cast<Id>(data_.size())});
        if (insert) {
            data_.push_back(&it->first);
        }
        return it->second;
    }

    const Data& operator[](Id id) const {
        return *data_[id];
    }
    
    size_t Size() const noexcept {
        return data_.size() * sizeof data_[0]
            + std::reduce(ids_.begin(), ids_.end(), 0ull, [](size_t a, const std::pair<const Data, Id>& b){
                return a + b.first.size() * sizeof b.first[0] + sizeof Id;
            });
    }

    size_t Capacity() const noexcept {
        return data_.capacity() * sizeof data_[0]
            + std::reduce(ids_.begin(), ids_.end(), 0ull, [](size_t a, const std::pair<const Data, Id>& b){
                return a + b.first.capacity() * sizeof b.first[0] + sizeof Id;
            });
    }

private:
    std::unordered_map<Data, Id> ids_;
    std::vector<const Data*> data_;
};