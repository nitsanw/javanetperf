#!/usr/bin/python

import csv
import os
import signal
import subprocess
import sys
import time

CLIENT = ("java", "-cp", "build", "ThreadClient")
NUMACTL = ("/home/evanj/numactl", "--physcpubind", "1")
PORT = 54321

TRIALS = 10
MAX_CLIENTS = 20

SERVERS = {
    'java-threads': NUMACTL + ("java", "-server", "-XX:+UseSerialGC", "-cp", "build", "ThreadServer"),
    'java-epoll': NUMACTL + ("java", "-server", "-XX:+UseSerialGC", "-cp", "build", "SelectServer"),
    'c++-threads': NUMACTL + ("./threadserver",),
    'c++-epoll': NUMACTL + ("./epollserver",),
}

def runClient(clients):
    """Runs the java ThreadClient with clients threads.
    Returns the requests per second as a float."""
    child = subprocess.Popen(CLIENT + ("localhost", str(PORT), str(clients)), stdout=subprocess.PIPE)
    output = ""
    for line in child.stdout:
        output += line
    error = child.wait()
    assert error == 0

    parts = output.split()
    assert parts[-1] == "reqs/s"
    return float(parts[-2])


def startServer(server_command):
    # Start the server, wait for it to start listening
    server = subprocess.Popen(server_command + (str(PORT),), stdout=subprocess.PIPE)
    time.sleep(1)
    # Verify it is still running
    assert server.poll() is None
    return server


def stopServer(server):
    if server.poll() is None:
        os.kill(server.pid, signal.SIGTERM)
        server.wait()


def testServer(server_command):
    server = startServer(server_command)

    try:
        results = []
        for num_clients in range(1, 6) + range(6, MAX_CLIENTS+1, 2):
            row = [num_clients]
            print num_clients, "clients", 
            sys.stdout.flush()
            for x in range(TRIALS):
                result = runClient(num_clients)
                row.append(result)
                print "\t", row[-1],
                sys.stdout.flush()
            results.append(row)
            print

        return results
    finally:
        stopServer(server)


if __name__ == "__main__":
    for server_name, server_command in SERVERS.iteritems():
        print
        print server_name
        results = testServer(server_command)
        f = open(server_name + ".csv", "wb")
        writer = csv.writer(f)
        writer.writerows(results)
        f.close()
