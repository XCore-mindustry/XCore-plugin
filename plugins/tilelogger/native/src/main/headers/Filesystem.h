#pragma once
#include <filesystem>
#include <string>

// Path::string() uses the Windows code page; ABI text and diagnostics must remain UTF-8.
inline std::string pathUtf8(const std::filesystem::path& path) {
    auto value = path.u8string();
    return std::string(value.begin(), value.end());
}
