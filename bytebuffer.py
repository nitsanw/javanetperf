#!/usr/bin/python

import csv
import subprocess


MEMORY = "512M"
#BASE_JAVA = ("taskset", "-c", "0,2", "java", "-server", "-Xmx" + MEMORY, "-Xms" + MEMORY, "-server", "-cp", "build/java")
BASE_JAVA = ("java", "-server", "-Xmx" + MEMORY, "-Xms" + MEMORY, "-server", "-cp", "build/java")

TEST = "ByteBufferTest"


def runJava(class_name, args):
    command = BASE_JAVA + (class_name,)+ args
    return subprocess.Popen(command, stdout=subprocess.PIPE)


def runTest(buffer_type, byte_order, buffer_size):
    process = runJava(TEST, (buffer_type, byte_order, str(buffer_size)))
    
    sections = []
    current = []
    for line in process.stdout:
        if line == "\n":
            sections.append(current)
            current = []
        else:
            current.append(line)

    code = process.wait()
    assert code == 0

    # Only take the last 3 sections
    out = {}
    for i, section in enumerate(sections[-3:]):
        for line in section:
            line = line.strip()
            key, values = line.split(": ")
            if key not in out:
                out[key] = []
            out[key].extend(float(v) for v in values.split())

    return out


def average(values):
    return float(sum(values)) / len(values)


def saveCSVResults(output_path, results):
    out = open(output_path, "wb")
    writer = csv.writer(out)
    writer.writerows(results)
    out.close()


if __name__ == "__main__":
    tests = {}
    for buffer_type in ("heap", "direct", "array"):
        for byte_order in ("be", "le"):
            for buffer_size in (1024, 2048, 4096, 8188, 8192, 8196, 16384):
                results = runTest(buffer_type, byte_order, buffer_size)
                print buffer_type, byte_order, buffer_size,
                for key, values in results.iteritems():
                    if key not in tests:
                        tests[key] = []
                    for value in values:
                        tests[key].append((buffer_type, byte_order, buffer_size, value))
                    print "%s: %f" % (key, average(values)),
                print

    for key, table in tests.iteritems():
        path = key.replace(" ", "_") + ".csv"
        saveCSVResults(path, table)
