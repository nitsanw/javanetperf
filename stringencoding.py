#!/usr/bin/python

import bytebuffer

TEST = "StringEncodingTest"
INPUT = "utf8.txt"

def runTest(conversion_type, reuse, destination):
    process = bytebuffer.runJava(TEST, (conversion_type, reuse, destination, INPUT))
    
    results = []
    for line in process.stdout:
        results.append(float(line.split()[0]))

    # Drop the first two results due to optimization sucking at first
    return results[2:]


if __name__ == "__main__":
    output = []
    #"unsafe3", "unsafe2", "unsafe",
    for conversion_type in ("unsafe3", "unsafe3", "unsafe3", "chars2","chars2","chars2", "string","string","string"):
        reuse = "reuse"
        destination = "buffer"
        results = runTest(conversion_type, reuse, destination)
        print conversion_type, reuse, destination, min(results)

        for value in results:
            output.append((conversion_type, reuse, destination, value))

    bytebuffer.saveCSVResults("stringencoding.csv", output)
