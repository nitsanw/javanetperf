#!/usr/bin/python

import csv
import os
import signal
import subprocess
import sys
import tempfile
import time

import statistics

CLIENT = ("java", "-cp", "build", "ThreadClient")
#~ NUMACTL = ("/home/evanj/numactl", "--physcpubind", "1")
#~ NUMACTL = ("numactl", "--physcpubind", "1")
#~ NUMACTL = ("numactl", "--cpubind", "1")
NUMACTL = tuple()
PORT = 54321

TRIALS = 10
MAX_CLIENTS = 20

SERVERS = {
    #~ 'java-threads': NUMACTL + ("java", "-server", "-XX:+UseSerialGC", "-cp", "build", "ThreadServer"),
    'java-epoll': NUMACTL + ("java", "-server", "-XX:+UseSerialGC", "-cp", "build", "SelectServer"),
    #~ 'c++-threads': NUMACTL + ("./threadserver",),
    'c++-epoll': NUMACTL + ("./epollserver",),
}

def runClient(host, port, clients):
    """Runs the java ThreadClient with clients threads against server running on host:port.
    Returns the requests per second as a float."""

    latency_out = tempfile.NamedTemporaryFile()
    child = subprocess.Popen(CLIENT + (host, str(port), str(clients), latency_out.name), stdout=subprocess.PIPE)
    output = ""
    for line in child.stdout:
        output += line
    error = child.wait()
    assert error == 0

    latency_f = open(latency_out.name)
    latencies = [int(l) for l in latency_f]
    latency_f.close()
    latency_out.close()

    average_latency, median_latency, stddev, min, max, latency_confidence = statistics.stats(latencies)

    parts = output.split()
    assert parts[-1] == "us"
    assert parts[-5] == "msgs/s"
    throughput = float(parts[-6])

    return throughput, average_latency, median_latency, latency_confidence


def startServer(server_command, port):
    # Start the server, wait for it to start listening
    server = subprocess.Popen(server_command + (str(port),), stdout=subprocess.PIPE)
    time.sleep(1)
    # Verify it is still running
    assert server.poll() is None
    return server


def stopServer(server):
    if server.poll() is None:
        os.kill(server.pid, signal.SIGTERM)
        server.wait()


def testServer(host, port):
    """Runs the test against server running on host at port."""

    results = [("Number of clients", "Throughput (msgs/s)", "Average latency (us)")]
    for num_clients in range(1, 6) + range(6, MAX_CLIENTS+1, 2):
        print num_clients, "clients", 
        sys.stdout.flush()
        for x in range(TRIALS):
            throughput, latency_average, latency_confidence, latency_median = runClient(
                    host, port, num_clients)
            latency_average /= 1000.0
            latency_confidence /= 1000.0
            latency_median /= 1000.0
            results.append((num_clients, throughput, latency_average, latency_confidence, latency_median))
            print "\t %.0f (%.1f us)" % (throughput, latency_average),
            sys.stdout.flush()
        print

    return results


if __name__ == "__main__":
    MODE_LOCALHOST = 0
    MODE_SERVER = 1
    MODE_CLIENT = 2

    mode = None
    host = None

    if len(sys.argv) == 1:
        mode = MODE_LOCALHOST
        host = "localhost"
    elif len(sys.argv) == 2:
        assert sys.argv[1] == "--server"
        mode = MODE_SERVER
    elif len(sys.argv) == 3:
        mode = MODE_CLIENT
        assert sys.argv[1] == "--client"
        host = sys.argv[2]
    else:
        sys.stderr.write("perftest.py: run localhost test\n")
        sys.stderr.write("perftest.py --server: run servers\n")
        sys.stderr.write("perftest.py --client host: run client against servers on host\n")
        sys.exit(1)

    servers = []
    try:
        if mode == MODE_LOCALHOST or mode == MODE_SERVER:        
            # Start all the servers
            for server_name, server_command in SERVERS.iteritems():
                port = PORT + len(servers)
                print server_name, port
                server = startServer(server_command, port)
                servers.append(server)

        if mode == MODE_SERVER:
            # Wait for them to exit
            for server in servers:
                result = server.wait()
                assert result == 0
            servers = []
        else:
            # Actually run the test
            for i, (server_name, server_command) in enumerate(SERVERS.iteritems()):
                port = PORT + i
                print
                print server_name, port
                results = testServer(host, port)
                f = open(server_name + ".csv", "wb")
                writer = csv.writer(f)
                writer.writerows(results)
                f.close()

    finally:
        for server in servers:
            stopServer(server)
