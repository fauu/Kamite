/*
 * Adapted from JSON for Modern C++ - https://github.com/nlohmann/json
 *
 *  Copyright © 2013-2022 Niels Lohmann
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

#include <string>

// enable ssize_t on MinGW
#ifdef __GNUC__
    #ifdef __MINGW32__
        #include <sys/types.h>
    #endif
#endif

// enable ssize_t for MSVC
#ifdef _MSC_VER
    #include <basetsd.h>
    using ssize_t = SSIZE_T;
#endif

/*!
@brief calculates the extra space to escape a JSON string

@param[in] s  the string to escape
@return the number of characters required to escape string @a s

@complexity Linear in the length of string @a s.
*/
static std::size_t extra_space(const std::string& s) noexcept
{
    std::size_t result = 0;

    for (const auto& c : s)
    {
        switch (c)
        {
            case '"':
            case '\\':
            case '\b':
            case '\f':
            case '\n':
            case '\r':
            case '\t':
            {
                // from c (1 byte) to \x (2 bytes)
                result += 1;
                break;
            }

            default:
            {
                if (c >= 0x00 and c <= 0x1f)
                {
                    // from c (1 byte) to \uxxxx (6 bytes)
                    result += 5;
                }
                break;
            }
        }
    }

    return result;
}

/*!
@brief escape a string

Escape a string by replacing certain special characters by a sequence of an
escape character (backslash) and another character and other control
characters by a sequence of "\u" followed by a four-digit hex
representation.

@param[in] s  the string to escape
@return  the escaped string

@complexity Linear in the length of string @a s.
*/
static std::string escape_string(const std::string& s) noexcept
{
    const auto space = extra_space(s);
    if (space == 0)
    {
        return s;
    }

    // create a result string of necessary size
    std::string result(s.size() + space, '\\');
    std::size_t pos = 0;

    for (const auto& c : s)
    {
        switch (c)
        {
            // quotation mark (0x22)
            case '"':
            {
                result[pos + 1] = '"';
                pos += 2;
                break;
            }

            // reverse solidus (0x5c)
            case '\\':
            {
                // nothing to change
                pos += 2;
                break;
            }

            // backspace (0x08)
            case '\b':
            {
                result[pos + 1] = 'b';
                pos += 2;
                break;
            }

            // formfeed (0x0c)
            case '\f':
            {
                result[pos + 1] = 'f';
                pos += 2;
                break;
            }

            // newline (0x0a)
            case '\n':
            {
                result[pos + 1] = 'n';
                pos += 2;
                break;
            }

            // carriage return (0x0d)
            case '\r':
            {
                result[pos + 1] = 'r';
                pos += 2;
                break;
            }

            // horizontal tab (0x09)
            case '\t':
            {
                result[pos + 1] = 't';
                pos += 2;
                break;
            }

            default:
            {
                if (c >= 0x00 and c <= 0x1f)
                {
                    // print character c as \uxxxx
                    sprintf(&result[pos + 1], "u%04x", int(c));
                    pos += 6;
                    // overwrite trailing null character
                    result[pos] = '\\';
                }
                else
                {
                    // all other characters are added as-is
                    result[pos++] = c;
                }
                break;
            }
        }
    }

    return result;
}
