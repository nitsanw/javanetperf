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
    for conversion_type in ("bytebuffer", "string", "chars", "custom"):
        for reuse in ("once", "reuse"):
            for destination in ("array", "buffer", "bytebuffer"):
                results = runTest(conversion_type, reuse, destination)
                print conversion_type, reuse, destination, bytebuffer.average(results)

                for value in results:
                    output.append((conversion_type, reuse, destination, value))

    bytebuffer.saveCSVResults("stringencoding.csv", output)
