#pragma once

#include <cstdint>
#include <string>

#define WIN32_LEAN_AND_MEAN
#include <windows.h>

struct InfoForExtension {
	const char* name;
	int64_t value;
};

struct SentenceInfo {
	const InfoForExtension* infoArray;
	int64_t operator[](std::string propertyName) {
		for (auto info = infoArray; info->name; ++info) {
			if (propertyName == info->name) return info->value;
		}
		return *(int*)0xcccc = 0;
	}
};

struct SKIP {};
inline void Skip() { throw SKIP(); }
