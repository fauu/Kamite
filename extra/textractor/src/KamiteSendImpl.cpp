#include "TextractorExtension.h"
#include "KamiteSend.h"

extern "C" __declspec(dllexport) wchar_t* OnNewSentence(wchar_t* sentence, const InfoForExtension* sentenceInfo) {
	try {
		std::wstring sentenceCopy(sentence);
		int oldSize = sentenceCopy.size();
		if (ProcessSentence(sentenceCopy, SentenceInfo{ sentenceInfo })) {
			if (sentenceCopy.size() > oldSize) {
				sentence = (wchar_t*)HeapReAlloc(
					GetProcessHeap(),
					HEAP_GENERATE_EXCEPTIONS,
					sentence,
					(sentenceCopy.size() + 1) * sizeof(wchar_t));
			}
			wcscpy_s(sentence, sentenceCopy.size() + 1, sentenceCopy.c_str());
		}
	} catch (SKIP) {
		*sentence = L'\0';
	}
	return sentence;
}
