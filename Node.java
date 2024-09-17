import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLOutput;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;

public class Node {
    private final ArrayList<SimpleEntry<String, Integer>> neighborList = new ArrayList<>();
    private Integer requestNum = 0;

    public void printNeighbors(ArrayList<SimpleEntry<String, Integer>> neighborList) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Neighbor List: ");
        for (int i = 0; i < neighborList.size(); i++) {
            stringBuilder.append(neighborList.get(i).getKey());
            stringBuilder.append(":");
            stringBuilder.append(neighborList.get(i).getValue().toString());
            if (i != neighborList.size() - 1) {
                stringBuilder.append(", ");
            }
        }
        System.out.println(stringBuilder);
    }


    public void startNode() {
        try {
            // start up the server
            ServerSocket serverSocket = new ServerSocket(0);
            String serverHostName = InetAddress.getLocalHost().getHostName();
            System.out.println(serverHostName);
            Integer serverPortNumber = serverSocket.getLocalPort();

            // open up file containing nodes
            File nodeFile = new File("nodeList");
            if (!nodeFile.exists()) {
                ArrayList<SimpleEntry<String, Integer>> nodeList = new ArrayList<>();
                nodeList.add(new SimpleEntry<>(serverHostName, serverPortNumber));
                FileOutputStream fileOut = new FileOutputStream(nodeFile);
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(nodeList);
                out.flush();
                out.close();
                fileOut.close();
            }
            else {
                // read in the node list
                ArrayList<SimpleEntry<String, Integer>> nodeList = new ArrayList<>();
                FileInputStream fileIn = new FileInputStream(nodeFile);
                ObjectInputStream objectIn = new ObjectInputStream(fileIn);
                try {
                    nodeList = (ArrayList<SimpleEntry<String, Integer>>) objectIn.readObject();
                }
                catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                fileIn.close();
                objectIn.close();

                // get random neighbor
                Random rand = new Random(System.currentTimeMillis());
                int randomIdx = rand.nextInt(nodeList.size());
                String neighborAddress = nodeList.get(randomIdx).getKey();
                Integer neighborPort = nodeList.get(randomIdx).getValue();

                // add neighbor to neighborList
                this.neighborList.add(new SimpleEntry<>(neighborAddress, neighborPort));

                // tell neighbor to output list of neighbors, unless it's bootstrap
                this.printNeighbors(this.neighborList);

                // connect to neighbor
                Socket neighborSocket = new Socket(neighborAddress, neighborPort);

                // tell neighbor server port
                PrintWriter output = new PrintWriter(neighborSocket.getOutputStream(), true);
                output.println(serverPortNumber);

                // close everything
                output.close();
                neighborSocket.close();

                // update node list
                nodeList.add(new SimpleEntry<>(InetAddress.getLocalHost().getHostName(), serverPortNumber));
                FileOutputStream fileOut = new FileOutputStream(nodeFile, false);
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(nodeList);
                out.flush();
                out.close();
                fileOut.close();

            }

            Thread serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (true) {
                            Socket clientSocket = serverSocket.accept();
                            ClientHandler clientHandler = new ClientHandler(clientSocket);
                            new Thread(clientHandler).start();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            serverThread.start();

            // take in user input (file names)
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("Enter the desired file's name: ");
                String fileName = scanner.nextLine();

                // send out a request for the file to the neighbors
                String request = serverHostName + " " + serverPortNumber + " " + fileName + " " + requestNum.toString();
                RequestHandler requestHandler = new RequestHandler(request);
                Thread requestThread = new Thread(requestHandler);
                requestThread.start();
                try {
                    requestThread.join();
                    ArrayList<SimpleEntry<String, Integer>> neighborsWithFile = requestHandler.getNeighborsWithFile();
                } catch(InterruptedException e) {
                    e.printStackTrace();
                }
                requestNum++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler implements Runnable {
        Socket clientSocket;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            if (neighborList.contains(new SimpleEntry<>(clientSocket.getInetAddress().getHostName(), clientSocket.getPort()))) {
                System.out.println("Already a neighbor!");
                // check for file
                // if we have the file, respond with address and port
                // otherwise, decrement the hop count
                // if the hop count > 0, send it to our neighbors
            } else {
                String clientAddress = clientSocket.getInetAddress().getHostName();
                System.out.println(clientAddress);
                int clientServerPort = 0;
                try {
                    BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    String inputLine = input.readLine();
                    clientServerPort = Integer.parseInt(inputLine);
                    input.close();
                    this.clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                neighborList.add(new SimpleEntry<>(clientAddress, clientServerPort));
                printNeighbors(neighborList);
            }
        }
    }

    private class RequestHandler implements Runnable {
        private final ArrayList<SimpleEntry<String, Integer>> neighborsWithFile = new ArrayList<>();
        String request;
        int hopCount;

        public RequestHandler(String request) {
            this.request = request;
            this.hopCount = 1;
        }

        public ArrayList<SimpleEntry<String, Integer>> getNeighborsWithFile() {
            return neighborsWithFile;
        }

        public void run() {
            boolean fileFound = false;

            while (!fileFound && hopCount < 16) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                ArrayList<Thread> threads = new ArrayList<>();

                Callable<Void> issueRequests = () -> {
                    // for each neighbor, send out a request (create a thread for each)
                    for (SimpleEntry<String, Integer> neighbor : neighborList) {
                        RequestSender sender = new RequestSender(neighbor.getKey(), neighbor.getValue(), this.request);
                        Thread thread = new Thread(sender);
                        threads.add(thread);
                        thread.start();
                    }
                    for (Thread thread : threads) {
                        thread.join();
                    }

                    return null;
                };

                Future<Void> future = executor.submit(issueRequests);

                try {
                    future.get(hopCount, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    e.printStackTrace();
                    future.cancel(true);
                } finally {
                    executor.shutdown();
                }

                hopCount *= 2;
            }
        }

        private class RequestSender implements Runnable {
            private String response = "";
            private String request = "";
            private Socket receiverSocket;

            public RequestSender(String receiverAddress, int receiverPort, String request) {
                this.request = request;
                try {
                    this.receiverSocket = new Socket(receiverAddress, receiverPort);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            public String getResponse() {
                return this.response;
            }
            public void run() {
                // write to the socket
                try {
                    PrintWriter output = new PrintWriter(this.receiverSocket.getOutputStream());
                    BufferedReader input =  new BufferedReader(new InputStreamReader(receiverSocket.getInputStream()));
                    System.out.println(this.request);
                    output.println(this.request);
                    this.response = input.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public static void main(String[] args){
        Node node = new Node();
        node.startNode();
    }
}