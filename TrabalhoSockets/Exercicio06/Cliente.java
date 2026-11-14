package Exercicio06;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Console;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cliente do servidor de arquivos: login, upload e download.
 * Os downloads são salvos na pasta downloads/ (no diretório onde o cliente foi executado).
 */
public class Cliente {

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5006;
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, charsetConsole()));

        try (Socket socket = new Socket(host, porta);
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            System.out.println("Conectado ao servidor de arquivos " + host + ":" + porta);
            boolean logado = false;
            while (!logado) {
                System.out.print("Usuário: ");
                String u = teclado.readLine();
                System.out.print("Senha: ");
                String s = teclado.readLine();
                if (u == null || s == null) {
                    return;
                }
                out.writeUTF("LOGIN");
                out.writeUTF(u.trim());
                out.writeUTF(s.trim());
                out.flush();
                logado = in.readBoolean();
                System.out.println(in.readUTF());
            }

            while (true) {
                System.out.println("\n[1] Upload  [2] Download  [0] Sair");
                System.out.print("> ");
                String op = teclado.readLine();
                if (op == null || op.trim().equals("0")) {
                    out.writeUTF("SAIR");
                    out.flush();
                    break;
                }
                switch (op.trim()) {
                    case "1" -> {
                        System.out.print("Caminho do arquivo local: ");
                        String caminho = teclado.readLine();
                        if (caminho != null) upload(Paths.get(caminho.trim().replace("\"", "")), in, out);
                    }
                    case "2" -> {
                        System.out.print("Nome do arquivo no servidor: ");
                        String nome = teclado.readLine();
                        if (nome != null) download(nome.trim(), in, out);
                    }
                    default -> System.out.println("Opção inválida.");
                }
            }
        }
        System.out.println("Até mais!");
    }

    static void upload(Path arquivo, DataInputStream in, DataOutputStream out) throws IOException {
        if (!Files.isRegularFile(arquivo)) {
            System.out.println("Arquivo local não encontrado: " + arquivo);
            return;
        }
        long tamanho = Files.size(arquivo);
        out.writeUTF("UPLOAD");
        out.writeUTF(arquivo.getFileName().toString());
        out.writeLong(tamanho);
        try (InputStream arq = Files.newInputStream(arquivo)) {
            arq.transferTo(out);
        }
        out.flush();
        boolean ok = in.readBoolean();
        System.out.println((ok ? "" : "Erro: ") + in.readUTF());
    }

    static void download(String nome, DataInputStream in, DataOutputStream out) throws IOException {
        out.writeUTF("DOWNLOAD");
        out.writeUTF(nome);
        out.flush();
        if (!in.readBoolean()) {
            System.out.println("Erro: " + in.readUTF());
            return;
        }
        long tamanho = in.readLong();
        Path pasta = Paths.get("downloads");
        Files.createDirectories(pasta);
        Path destino = pasta.resolve(Paths.get(nome).getFileName());
        try (OutputStream arq = Files.newOutputStream(destino)) {
            byte[] buffer = new byte[8192];
            long restante = tamanho;
            while (restante > 0) {
                int lidos = in.read(buffer, 0, (int) Math.min(buffer.length, restante));
                if (lidos < 0) {
                    throw new IOException("conexão encerrada no meio do download");
                }
                arq.write(buffer, 0, lidos);
                restante -= lidos;
            }
        }
        System.out.println("Download concluído: " + destino.toAbsolutePath() + " (" + tamanho + " bytes)");
    }

    static Charset charsetConsole() {
        Console c = System.console();
        return c != null ? c.charset() : Charset.defaultCharset();
    }
}
