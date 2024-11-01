import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;

public class BannerGrabber {
    private static final int TIMEOUT = 3000; // 3 seconds
    
    public static class ScanResult {
        public final String ip;
        public final int port;
        public final String banner;
        
        public ScanResult(String ip, int port, String banner) {
            this.ip = ip;
            this.port = port;
            this.banner = banner;
        }
    }

    public static String grabBanner(String ip, int port) throws IOException {
        try (Socket socket = new Socket()) {
            // Set aggressive timeouts to avoid hanging
            socket.setSoTimeout(TIMEOUT);
            socket.connect(new InetSocketAddress(ip, port), TIMEOUT);

            // Many services send banner immediately upon connection
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                
                // Some services need a prompt
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("HEAD / HTTP/1.0\r\n"); // HTTP prompt
                writer.println("HELP\r\n"); // FTP/SMTP prompt
                
                StringBuilder banner = new StringBuilder();
                String line;
                // Read first few lines that might contain banner info
                for(int i = 0; i < 10 && (line = reader.readLine()) != null; i++) {
                    banner.append(line).append("\n");
                }
                return banner.toString();
            }
        } catch (IOException e) {
            return "No banner available: " + e.getMessage();
        }
    }

    public static List<ScanResult> scanRange(String startIp, String endIp, int[] ports) {
        List<ScanResult> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(50); // 50 concurrent scans
        List<Future<ScanResult>> futures = new ArrayList<>();

        try {
            long start = ipToLong(startIp);
            long end = ipToLong(endIp);

            for (long ip = start; ip <= end; ip++) {
                String currentIp = longToIp(ip);
                for (int port : ports) {
                    futures.add(executor.submit(() -> {
                        String banner = grabBanner(currentIp, port);
                        return new ScanResult(currentIp, port, banner);
                    }));
                }
            }

            // Collect results
            for (Future<ScanResult> future : futures) {
                try {
                    results.add(future.get(TIMEOUT, TimeUnit.MILLISECONDS));
                } catch (Exception e) {
                    // Handle timeouts and errors
                }
            }
        } finally {
            executor.shutdown();
        }
        
        return results;
    }

    // Helper method to convert IP to long
    private static long ipToLong(String ipAddress) {
        String[] ipAddressInArray = ipAddress.split("\\.");
        long result = 0;
        for (int i = 0; i < ipAddressInArray.length; i++) {
            int power = 3 - i;
            result += Long.parseLong(ipAddressInArray[i]) * Math.pow(256, power);
        }
        return result;
    }

    // Helper method to convert long to IP
    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >> 8) & 0xFF) + "." +
               (ip & 0xFF);
    }

    public static void main(String[] args) {
        int[] commonPorts = {21, 22, 23, 25, 80, 443, 3306, 5432};
        List<ScanResult> results = scanRange("192.168.1.1", "192.168.1.255", commonPorts);
        
        for (ScanResult result : results) {
            if (!result.banner.contains("No banner available")) {
                System.out.println("IP: " + result.ip + 
                                 " Port: " + result.port + 
                                 "\nBanner: " + result.banner);
            }
        }
    }
}
