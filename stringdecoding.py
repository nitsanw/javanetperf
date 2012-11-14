#!/usr/bin/python

import bytebuffer

TEST = "StringDecodingTest"
INPUT = "utf8.txt"

def runTest(conversion_type, reuse):
    process = bytebuffer.runJava(TEST, (conversion_type, reuse, INPUT))
    
    results = []
    for line in process.stdout:
        results.append(float(line.split()[0]))

    # Drop the first result due to optimization sucking at first
    return results[1:]


if __name__ == "__main__":
    output = []
    for conversion_type in ("chardecoder", "string", "custom"):
        for reuse in ("once", "reuse"):
            results = runTest(conversion_type, reuse)
            print conversion_type, reuse, bytebuffer.average(results)

            for value in results:
                output.append((conversion_type, reuse, value))

    bytebuffer.saveCSVResults("stringdecoding.csv", output)
