#!/usr/bin/python

import csv
import tempfile

import perftest

if __name__ == "__main__":
    for server_name, server_command in perftest.SERVERS.iteritems():
        print
        print server_name

        server = perftest.startServer(server_command)
        try:
            
        finally:
            perftest.stopServer(server)

        latency_out = tempfile.NamedTemporaryFile()
        results = perftest.testServer(server_command)
        f = open(server_name + ".csv", "wb")
        writer = csv.writer(f)
        writer.writerows(results)
        f.close()
