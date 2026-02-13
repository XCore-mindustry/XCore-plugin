#pragma once
#include <vector>
#include <filesystem>
#include <fstream>
#include <exception>
#include <iostream>

class SubnetFilter {
public:
    void reset(const std::filesystem::path& accept, const std::filesystem::path& deny) {
        accept_ = parse_list(accept);
        deny_ = parse_list(deny);
    }

    bool accepted(const std::string& subnet) const {
        uint32_t ip = parse_subnet(subnet).ip;
        for (const Subnet& sn : accept_) {
            if ((ip & sn.mask) == sn.ip)
                return true;
        }
        for (const Subnet& sn : deny_) {
            if ((ip & sn.mask) == sn.ip)
                return false;
        }
        return true;
    }

private:
    struct Subnet {
        uint32_t ip = 0;
        uint32_t mask = ~0ul;
    };

    static std::vector<Subnet> parse_list(const std::filesystem::path& path) {
        std::vector<Subnet> subnets;
        std::ifstream ifs(std::filesystem::canonical(path));
        if (!ifs)
            std::cerr << "parse_list() failed: " << path << std::endl;
        std::string str;
        while (std::getline(ifs, str)) {
            trim(str);
            if (str.size())
                subnets.push_back(parse_subnet(str));
        }
        return subnets;
    }

    static Subnet parse_subnet(const std::string& str) {
        Subnet subnet;
        auto tokens = split(str, "/");
        if (tokens.size() < 1 || tokens.size() > 2)
            throw std::runtime_error("parse_subnet() failed: " + str);
        if (tokens.size() == 2) {
            uint32_t mask = std::stoul(tokens[1]);
            if (mask > 32)
                throw std::runtime_error("parse_subnet() failed: " + str);
            subnet.mask <<= 32 - mask;
        }
        tokens = split(tokens[0], ".");
        if (tokens.size() != 4)
            throw std::runtime_error("parse_subnet() failed: " + str);
        int i = 24;
        for (const std::string& t : tokens) {
            uint32_t octet = std::stoul(t);
            if (octet > 255)
                throw std::runtime_error("parse_subnet() failed: " + str);
            subnet.ip |= octet << i;
            i -=8;
        }
        return subnet;
    }

    static std::vector<std::string> split(const std::string& str, const std::string& delim) {
        size_t last = 0;
        size_t next = 0;
        std::vector<std::string> vec;
        while ((next = str.find(delim, last)) != std::string::npos) {
            vec.push_back(str.substr(last, next-last));
            last = next + 1;
        }
        vec.push_back(str.substr(last));
        return vec;
    }

    static inline void trim(std::string &s) {
        s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](unsigned char ch) {
            return !std::isspace(ch);
        }));
        s.erase(std::find_if(s.rbegin(), s.rend(), [](unsigned char ch) {
            return !std::isspace(ch);
        }).base(), s.end());
    }

    std::vector<Subnet> accept_;
    std::vector<Subnet> deny_;
};