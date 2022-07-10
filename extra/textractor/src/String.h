/*
 * Adapted from Textractor - https://github.com/Artikash/Textractor
 * (https://github.com/Artikash/Textractor/blob/master/LICENSE)
 *
 * For Kamite project license information, please see the COPYING.md file.
 */
#pragma once

#include <string>
#include <vector>

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#define CP_UTF8 65001

inline std::string WideStringToString(const std::wstring& text) {
	std::vector<char> buffer((text.size() + 1) * 4);
	WideCharToMultiByte(CP_UTF8, 0, text.c_str(), -1, buffer.data(), buffer.size(), nullptr, nullptr);
	return buffer.data();
}
