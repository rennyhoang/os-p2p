import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

public class BootstrapNode {
    String address;
    Integer portNumber;
    ArrayList<SimpleEntry<String, Integer>> nodeList = new ArrayList<>();
    ArrayList<SimpleEntry<String, Integer>> neighborList = new ArrayList<>();

    public BootstrapNode(String address, Integer portNumber) {
        this.address = address;
        this.portNumber = portNumber;
        this.nodeList.add(new SimpleEntry<>(address, portNumber));
    }

    public void startServer() {
        try {
            ServerSocket serverSocket = new ServerSocket(portNumber);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected");
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                new Thread(clientHandler).start();
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
            // decide neighbor
            Random rand = new Random();
            int randomIndex = rand.nextInt(nodeList.size());
            String neighborAddress = nodeList.get(randomIndex).getKey();
            Integer neighborPort = nodeList.get(randomIndex).getValue();

            // if the new neighbor is bootstrap
            if (neighborAddress.equals(address) && Objects.equals(neighborPort, portNumber)) {
                neighborList.add(new SimpleEntry<>(clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort()));
                System.out.println(neighborList.toString());
            }

            // send the address and port, back to the client
            try {
                PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
                output.println(neighborAddress);
                output.println(neighborPort);
                output.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args){
        BootstrapNode bootstrapNode = new BootstrapNode("127.0.0.1", 8080);
        bootstrapNode.startServer();
    }
}
