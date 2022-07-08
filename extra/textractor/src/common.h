/*
 * Adapted from Textractor - https://github.com/Artikash/Textractor
 * Licensed under GNU GPL v3.0 (https://github.com/Artikash/Textractor/blob/master/LICENSE)
 */

#include <windows.h>
#include <string>
#include <vector>

#define CP_UTF8 65001

#ifdef _WIN64
constexpr bool x64 = true;
#else
constexpr bool x64 = false;
#endif

inline std::string WideStringToString(const std::wstring& text)
{
	std::vector<char> buffer((text.size() + 1) * 4);
	WideCharToMultiByte(CP_UTF8, 0, text.c_str(), -1, buffer.data(), buffer.size(), nullptr, nullptr);
	return buffer.data();
}
