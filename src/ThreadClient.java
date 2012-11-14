import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreadClient {
    private static class ClientThread extends Thread {
        public ClientThread(ServerBase.Client client, AtomicBoolean count, AtomicBoolean quit,
                LatencyRecord latencies) {
            this.client = client;
            this.count = count;
            this.quit = quit;
            this.latencies = latencies;

            this.client.setMessage(new byte[]{0x1, 0x2, 0x3, 0x4});
        }

        public void run() {
            long start = System.nanoTime();
            long end = -1;
            while (!quit.get()) {
                client.writeMessage();
                client.readMessage();
                if (latencies != null) end = System.nanoTime();
                if (count.get()) {
                    if (latencies != null) {
                        long diff = end - start;
                        assert 0 <= diff && diff <= Integer.MAX_VALUE;
                        latencies.add((int) diff);
                    }
                    requests += 1;
                }
                // Call nanoTime() once per iteration: computation should be negligible
                start = end;
            }
        }

        private final ServerBase.Client client;
        private final AtomicBoolean count;
        private final AtomicBoolean quit;
        private final LatencyRecord latencies;
        public int requests = 0;
    }

    private final static int WARM_UP_SECS = 5;
    private final static int MEASURE_SECS = 30;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (!(2 <= args.length && args.length <= 4)) {
            System.err.println("ThreadClient [server address] [server port] [threads = 1] [latency output file]");
            System.exit(1);
        }

        InetAddress address = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        int clients = 1;
        if (args.length >= 3) clients = Integer.parseInt(args[2]);
        PrintWriter latencyOut = null;
        if (args.length >= 4) latencyOut = new PrintWriter(args[3]);

        AtomicBoolean count = new AtomicBoolean(false);
        AtomicBoolean quit = new AtomicBoolean(false);
        ClientThread[] threads = new ClientThread[clients];
        LatencyRecord[] latencies = new LatencyRecord[clients];
        for (int i = 0; i < threads.length; ++i) {
            Socket s = new Socket(address, port);
            ServerBase.Client c = new ServerBase.Client(s);

            if (latencyOut != null) {
                latencies[i] = new LatencyRecord();
            }
            threads[i] = new ClientThread(c, count, quit, latencies[i]);
            threads[i].start();
        }

        Thread.sleep(WARM_UP_SECS * 1000);
        long start = System.currentTimeMillis();
        count.set(true);
        Thread.sleep(MEASURE_SECS * 1000);
        count.set(false);
        long end = System.currentTimeMillis();
        // Provide a short "cool down" period to ensure that the load is
        // constant while all threads finish executing.
        Thread.sleep(500);
        quit.set(true);

        int total = 0;
        for (ClientThread t : threads) {
            t.join();
            total += t.requests;
        }

        // Write out the latencies and sum them
        long latencySum = 0;
        int latencyCount = 0;
        if (latencyOut != null) {
            for (LatencyRecord r : latencies ) {
                for (Integer latencyNS : r) {
                    latencySum += latencyNS;
                    latencyOut.println(latencyNS);
                }
                latencyCount += r.size();
            }
            latencyOut.close();
        }

        long ms = end - start;
        double msgsPerSec = (double)total/ms*1000;
        System.out.printf("%d requests in %d ms = %.3f msgs/s", total, ms, msgsPerSec);
        if (latencyOut != null) {
            System.out.printf(" average latency: %.3f us", ((double)latencySum/latencyCount/1000.0));
        }
        System.out.println();
    }
}

/** Provides efficient writing of integers. Ideally, there are no allocations
while running the test, but even if there are it should be fast (O(1)). */
class LatencyRecord implements Iterable<Integer> {
    public LatencyRecord() {
        newBlock();
    }

    public void add(int value) {
        if (nextIndex == block.length) {
            newBlock();
        }
        block[nextIndex] = value;
        nextIndex += 1;
    }

    private void newBlock() {
        block = new int[BLOCK_SIZE];
        blocks.add(block);
        nextIndex = 0;
    }

    public int size() {
        // Sum the length of all the blocks
        int s = 0;
        for (int[] b : blocks) {
            s += b.length;
        }

        // Remove the current block and add back just the filled slots
        s -= block.length;
        s += nextIndex;
        return s;
    }

    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {
            public boolean hasNext() {
                if (blockIterator.hasNext()) {
                    // not end block
                    return index < block.length;
                } else {
                    // last block
                    return index < nextIndex;
                }
            }

            public Integer next() {
                int value = block[index];
                index += 1;
                if (blockIterator.hasNext()) {
                    if (index == block.length) {
                        block = blockIterator.next();
                        index = 0;
                    }
                }

                return value;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private final Iterator<int[]> blockIterator = blocks.iterator();
            private int[] block = blockIterator.next();
            private int index = 0;
        };
    }

    // 1 M = 4MB = 1k pages; more than enough for 25000 msgs/s for 30 seconds
    private final static int BLOCK_SIZE = 1 << 20;
    private final LinkedList<int[]> blocks = new LinkedList<int[]>();
    private int nextIndex = 0;
    private int[] block = null;
}
