package Exercicio07;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.UUID;

/**
 * Chat simples com multicast (UDP). Não existe servidor: cada participante entra no grupo
 * multicast, envia mensagens para o grupo e recebe as mensagens de todos.
 *
 * Uso: java Exercicio07.Chat [nome] [grupo = 230.0.0.1] [porta = 4446] [interface de rede]
 * Comandos no chat: /sair
 *
 * Formato do datagrama: <id do participante>|<tipo>|<nome>|<texto>   tipo = MSG, ENTROU ou SAIU
 */
public class Chat {

    static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) throws IOException, InterruptedException {
        System.setProperty("java.net.preferIPv4Stack", "true");
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, charsetConsole()));

        String nome = args.length > 0 ? args[0] : null;
        while (nome == null || nome.isBlank()) {
            System.out.print("Seu nome: ");
            nome = teclado.readLine();
            if (nome == null) {
                return;
            }
        }
        nome = nome.trim().replace("|", "");
        InetAddress grupo = InetAddress.getByName(args.length > 1 ? args[1] : "230.0.0.1");
        int porta = args.length > 2 ? Integer.parseInt(args[2]) : 4446;
        NetworkInterface rede = args.length > 3 ? NetworkInterface.getByName(args[3]) : escolherInterface();
        String meuId = UUID.randomUUID().toString().substring(0, 8);

        try (MulticastSocket socket = new MulticastSocket(porta)) {
            if (rede != null) {
                socket.setNetworkInterface(rede);
            }
            socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true); // recebe também da própria máquina
            socket.setTimeToLive(1); // só a rede local
            InetSocketAddress endereco = new InetSocketAddress(grupo, porta);
            socket.joinGroup(endereco, rede);

            System.out.println("Entrou no chat " + grupo.getHostAddress() + ":" + porta
                    + (rede != null ? " (interface " + rede.getDisplayName() + ")" : ""));
            System.out.println("Digite suas mensagens. /sair para sair.\n");

            Thread receptor = new Thread(() -> receber(socket, meuId));
            receptor.setDaemon(true);
            receptor.start();

            enviar(socket, grupo, porta, meuId, "ENTROU", nome, "");
            String linha;
            while ((linha = teclado.readLine()) != null) {
                if (linha.trim().equalsIgnoreCase("/sair")) {
                    break;
                }
                if (!linha.isBlank()) {
                    enviar(socket, grupo, porta, meuId, "MSG", nome, linha);
                }
            }
            enviar(socket, grupo, porta, meuId, "SAIU", nome, "");
            Thread.sleep(200); // dá tempo do datagrama sair antes de fechar
            socket.leaveGroup(endereco, rede);
        }
        System.out.println("Você saiu do chat.");
    }

    static void receber(MulticastSocket socket, String meuId) {
        byte[] buffer = new byte[4096];
        while (!socket.isClosed()) {
            try {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);
                String[] p = new String(pacote.getData(), 0, pacote.getLength(), StandardCharsets.UTF_8).split("\\|", 4);
                if (p.length < 4 || p[0].equals(meuId)) {
                    continue; // ignora pacotes estranhos e as próprias mensagens
                }
                String hora = LocalTime.now().format(HORA);
                switch (p[1]) {
                    case "ENTROU" -> System.out.println("[" + hora + "] *** " + p[2] + " entrou no chat ***");
                    case "SAIU" -> System.out.println("[" + hora + "] *** " + p[2] + " saiu do chat ***");
                    default -> System.out.println("[" + hora + "] " + p[2] + ": " + p[3]);
                }
            } catch (IOException e) {
                return; // socket fechado
            }
        }
    }

    static void enviar(MulticastSocket socket, InetAddress grupo, int porta, String id, String tipo,
                       String nome, String texto) throws IOException {
        byte[] dados = (id + "|" + tipo + "|" + nome + "|" + texto).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(dados, dados.length, grupo, porta));
    }

    /** Usa a interface da rota padrão (a placa de rede "principal"); se falhar, a primeira com multicast. */
    static NetworkInterface escolherInterface() {
        try (DatagramSocket teste = new DatagramSocket()) {
            teste.connect(InetAddress.getByName("8.8.8.8"), 53); // não envia nada, só escolhe a rota
            NetworkInterface ni = NetworkInterface.getByInetAddress(teste.getLocalAddress());
            if (ni != null && ni.supportsMulticast()) {
                return ni;
            }
        } catch (IOException ignored) {
            // sem rota externa (ex.: sem internet)
        }
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isUp() && ni.supportsMulticast() && !ni.isLoopback() && !ni.isVirtual()
                        && Collections.list(ni.getInetAddresses()).stream().anyMatch(a -> a instanceof Inet4Address)) {
                    return ni;
                }
            }
        } catch (SocketException ignored) {
        }
        return null;
    }

    static Charset charsetConsole() {
        Console c = System.console();
        return c != null ? c.charset() : Charset.defaultCharset();
    }
}
