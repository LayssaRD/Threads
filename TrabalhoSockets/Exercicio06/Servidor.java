package Exercicio06;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servidor de arquivos: o usuário faz login e pode apenas enviar (upload) e baixar (download).
 * Cada usuário tem sua própria pasta em armazenamento/<usuario>/.
 *
 * Protocolo binário com DataInput/DataOutputStream:
 *   LOGIN:    C: "LOGIN", usuario, senha               S: boolean ok, mensagem
 *   UPLOAD:   C: "UPLOAD", nome, long tamanho, bytes   S: boolean ok, mensagem
 *   DOWNLOAD: C: "DOWNLOAD", nome                      S: boolean ok, (long tamanho, bytes) ou mensagem
 *   SAIR:     C: "SAIR"
 * Os usuários ficam em usuarios.txt (usuario;senha), criado com usuários de exemplo.
 */
public class Servidor {

    static final Path RAIZ = Paths.get("armazenamento");
    static final Path USUARIOS = Paths.get("usuarios.txt");
    static final long TAMANHO_MAXIMO = 200L * 1024 * 1024; // 200 MB
    static final Map<String, String> usuarios = new HashMap<>();

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5006;
        carregarUsuarios();
        Files.createDirectories(RAIZ);

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Servidor de arquivos ouvindo na porta " + porta);
            System.out.println("Arquivos em: " + RAIZ.toAbsolutePath() + " | usuários: " + usuarios.keySet());
            while (true) {
                Socket cliente = servidor.accept();
                new Thread(() -> atender(cliente)).start();
            }
        }
    }

    static void atender(Socket socket) {
        String quem = socket.getRemoteSocketAddress().toString();
        String usuario = null;
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            System.out.println("[+] Conexão de " + quem);

            while (true) {
                String comando = in.readUTF();
                switch (comando) {
                    case "LOGIN" -> {
                        String u = in.readUTF();
                        String s = in.readUTF();
                        if (s.equals(usuarios.get(u))) {
                            usuario = u;
                            Files.createDirectories(RAIZ.resolve(u));
                            responder(out, true, "Login realizado. Bem-vindo, " + u + "!");
                            System.out.println("    " + quem + " logou como " + u);
                        } else {
                            responder(out, false, "Usuário ou senha inválidos.");
                        }
                    }
                    case "UPLOAD" -> {
                        String nome = in.readUTF();
                        long tamanho = in.readLong();
                        if (usuario == null || !nomeValido(nome) || tamanho < 0 || tamanho > TAMANHO_MAXIMO) {
                            descartar(in, tamanho);
                            responder(out, false, usuario == null ? "Faça login primeiro."
                                    : !nomeValido(nome) ? "Nome de arquivo inválido." : "Arquivo grande demais.");
                            break;
                        }
                        Path destino = RAIZ.resolve(usuario).resolve(nome);
                        Path temp = Files.createTempFile(RAIZ.resolve(usuario), "upload", ".tmp");
                        try (OutputStream arq = Files.newOutputStream(temp)) {
                            copiar(in, arq, tamanho);
                        }
                        Files.move(temp, destino, StandardCopyOption.REPLACE_EXISTING);
                        responder(out, true, "Upload de " + nome + " concluído (" + tamanho + " bytes).");
                        System.out.println("    " + usuario + " enviou " + nome + " (" + tamanho + " bytes)");
                    }
                    case "DOWNLOAD" -> {
                        String nome = in.readUTF();
                        Path arquivo = usuario == null || !nomeValido(nome) ? null : RAIZ.resolve(usuario).resolve(nome);
                        if (arquivo == null || !Files.isRegularFile(arquivo)) {
                            responder(out, false, usuario == null ? "Faça login primeiro." : "Arquivo não encontrado: " + nome);
                            break;
                        }
                        long tamanho = Files.size(arquivo);
                        out.writeBoolean(true);
                        out.writeLong(tamanho);
                        try (InputStream arq = Files.newInputStream(arquivo)) {
                            copiar(arq, out, tamanho);
                        }
                        out.flush();
                        System.out.println("    " + usuario + " baixou " + nome + " (" + tamanho + " bytes)");
                    }
                    case "SAIR" -> {
                        return;
                    }
                    default -> responder(out, false, "Comando desconhecido.");
                }
            }
        } catch (IOException e) {
            // cliente desconectou
        } finally {
            System.out.println("[-] " + quem + (usuario != null ? " (" + usuario + ")" : "") + " desconectou");
        }
    }

    /** Aceita só um nome simples, sem pastas (impede acessar arquivos de outros usuários). */
    static boolean nomeValido(String nome) {
        return nome.matches("(?U)[\\w\\-. ()]{1,120}") && !nome.equals(".") && !nome.equals("..");
    }

    static void responder(DataOutputStream out, boolean ok, String msg) throws IOException {
        out.writeBoolean(ok);
        out.writeUTF(msg);
        out.flush();
    }

    static void copiar(InputStream in, OutputStream out, long tamanho) throws IOException {
        byte[] buffer = new byte[8192];
        long restante = tamanho;
        while (restante > 0) {
            int lidos = in.read(buffer, 0, (int) Math.min(buffer.length, restante));
            if (lidos < 0) {
                throw new IOException("conexão encerrada no meio da transferência");
            }
            out.write(buffer, 0, lidos);
            restante -= lidos;
        }
    }

    static void descartar(DataInputStream in, long tamanho) throws IOException {
        if (tamanho > 0) {
            copiar(in, OutputStream.nullOutputStream(), tamanho);
        }
    }

    static void carregarUsuarios() throws IOException {
        if (!Files.exists(USUARIOS)) {
            Files.write(USUARIOS, List.of("aluno;1234", "professor;abcd"), StandardCharsets.UTF_8);
        }
        for (String l : Files.readAllLines(USUARIOS, StandardCharsets.UTF_8)) {
            String[] p = l.trim().split(";");
            if (p.length == 2) {
                usuarios.put(p[0], p[1]);
            }
        }
    }
}
