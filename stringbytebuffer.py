#!/usr/bin/python

import functools
import random
import tempfile

import bytebuffer

TEST = "StringByteBufferPerformance"
INPUT = "utf8.txt"

STRING_LENGTH_START = 8
STRING_LENGTH_END = (1 << 14) + 1
NUM_STRINGS = 1000
# All printable ASCII characters (minus tab and new line)
SINGLE_BYTE = u"".join(unichr(v) for v in xrange(32, 127))
# Two byte UTF-8 characters (Latin-1 supplement)
TWO_BYTE = u"".join(unichr(v) for v in xrange(0x00c0, 0x0100))
# three byte UTF-8 (Hiragana)
THREE_BYTE = u"".join(unichr(v) for v in xrange(0x3041, 0x3095))
# four byte (Ancient Greek Numbers
FOUR_BYTE = u"".join(unichr(v) for v in xrange(0x10140, 0x1018b))


def makeString(alphabet, length):
    s = []
    for j in xrange(string_length):
        s.append(random.choice(alphabet))
    assert len(s) == string_length
    return u"".join(s)


def makeString(alphabet, length):
    s = []
    for j in xrange(string_length):
        s.append(random.choice(alphabet))
    assert len(s) == string_length
    return u"".join(s)


PERCENTAGE = 0.10
def mixedString(string_length):
    s = []
    for j in xrange(string_length):
        if random.random() < PERCENTAGE:
            s.append(random.choice(TWO_BYTE))
        else:
            s.append(random.choice(SINGLE_BYTE))
    return u"".join(s)


CHARSETS = [
    ("1byte", functools.partial(makeString, SINGLE_BYTE)),
    ("1.1byte", mixedString),
    ("2byte", functools.partial(makeString, TWO_BYTE)),
    ("3byte", functools.partial(makeString, THREE_BYTE)),
    ("4byte", functools.partial(makeString, FOUR_BYTE)),
]


def runTest(encoder_type, input):
    process = bytebuffer.runJava(TEST, (encoder_type, input))

    results = []
    skip = 2
    for line in process.stdout:
        if skip > 0:
            skip -= 1
        else:
            results.append(float(line.split()[0]))

    # Drop the first two results due to optimization sucking at first
    return results[2:]


def powerOf2Range(start, max):
    while start < max:
        yield start
        start *= 2


if __name__ == "__main__":
    random.seed(None)

    # Build temporary files
    temp_files = []
    for charset_name, charset_generator in CHARSETS:
        for string_length in powerOf2Range(STRING_LENGTH_START, STRING_LENGTH_END):
            temp = tempfile.NamedTemporaryFile()
            for i in xrange(NUM_STRINGS):
                s = charset_generator(string_length)
                assert len(s) == string_length
                temp.write(s.encode("UTF-8"))
                temp.write("\n")
            temp.flush()
            temp_files.append((charset_name, string_length, temp))

    output = [("encoder", "charset", "string length", "chars per us")]
    for encoder_type in ("jdk", "generic", "bytebuffer", "reflect"):
        for charset_name, string_length, temp in temp_files:
            results = runTest(encoder_type, temp.name)
            print encoder_type, charset_name, string_length, bytebuffer.average(results)

            for value in results:
                output.append((encoder_type, charset_name, string_length, value))
    bytebuffer.saveCSVResults("stringbytebuffer.csv", output)
