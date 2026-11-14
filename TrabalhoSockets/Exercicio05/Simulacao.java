package Exercicio05;

/**
 * Dispara várias filiais ao mesmo tempo (cada uma em sua thread, com seu próprio socket).
 * Rode a Central antes.
 *
 * Uso: java Exercicio05.Simulacao [filiais = 5] [ocorrências = 1500] [delay máx. ms = 5] [host = localhost]
 */
public class Simulacao {

    public static void main(String[] args) throws InterruptedException {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int ocorrencias = args.length > 1 ? Integer.parseInt(args[1]) : 1500;
        int delay = args.length > 2 ? Integer.parseInt(args[2]) : 5;
        String host = args.length > 3 ? args[3] : "localhost";

        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            threads[i] = new Thread(new Filial(i + 1, ocorrencias, delay, host, 5005), "Filial " + (i + 1));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        System.out.println("Simulação encerrada: " + n + " filiais enviaram " + (n * ocorrencias) + " ocorrências.");
    }
}
