import random
import sys
import time
from collections import deque
from dataclasses import dataclass
from threading import Condition, Event, Lock, RLock, Thread


class Log:
    inicio = time.monotonic()
    trava = Lock()

    @classmethod
    def msg(cls, texto):
        with cls.trava:
            tempo = int((time.monotonic() - cls.inicio) * 1000)
            print(f"[{tempo:5d} ms] {texto}", flush=True)


@dataclass
class Pedido:
    cliente_id: int
    senha: int = 0
    entregue: bool = False


class Bar:
    def __init__(self, quantidade_garcons):
        self.garcons_ativos = quantidade_garcons
        self.trava = Lock()
        self.cancelado = Event()
        self.garcons = []

    def garcom_terminou(self):
        with self.trava:
            self.garcons_ativos -= 1
            if self.garcons_ativos == 0:
                Log.msg("BAR FECHADO para novos atendimentos")

    def cancelar(self, motivo):
        # Libera pausas e esperas caso o programa seja interrompido ou falhe.
        self.cancelado.set()
        for garcom in self.garcons:
            garcom.encerrar(motivo)


class Bartender:
    def __init__(self, cancelado):
        self.trava = Lock()
        self.cancelado = cancelado

    def preparar_pedidos(self, garcom_id, quantidade):
        # O lock permanece ocupado durante todo o preparo.
        with self.trava:
            if self.cancelado.is_set():
                return False
            Log.msg(f"  >> BARTENDER preparando {quantidade} pedido(s) "
                    f"do Garçom {garcom_id}")
            if self.cancelado.wait(0.4 * quantidade):
                return False
            Log.msg(f"  >> BARTENDER terminou os pedidos do Garçom {garcom_id}")
            return True


class Garcom(Thread):
    def __init__(self, id, capacidade, total_clientes, rodadas_total, bartender, bar):
        super().__init__(name=f"Garçom {id}")
        self.id = id
        self.capacidade = capacidade
        self.total_clientes = total_clientes
        self.rodadas_total = rodadas_total
        self.bartender = bartender
        self.bar = bar
        self.condicao = Condition(RLock())
        # Não usar maxlen: o limite é verificado antes de aceitar cada pedido.
        self.fila = deque()
        self.rodada = 1
        self.respostas_na_rodada = 0
        self.proxima_senha = 1
        self.senha_da_vez = 1
        self.atendendo = False
        self.encerrado = False

    def faz_pedido(self, cliente_id, ultima_rodada, pedido):
        with self.condicao:
            # Espera uma rodada elegível ANTES de retirar uma senha.
            while not self.encerrado and (
                self.atendendo or self.rodada <= ultima_rodada
            ):
                self.condicao.wait()
            if self.encerrado:
                return -1

            if pedido is not None:
                pedido.senha = self.proxima_senha
                self.proxima_senha += 1
                Log.msg(f"Cliente {cliente_id} AGUARDA VAGA no Garçom {self.id} "
                        f"(senha {pedido.senha}; pedido ainda não aceito)")
                while not self.encerrado and (
                    self.atendendo or len(self.fila) >= self.capacidade
                    or pedido.senha != self.senha_da_vez
                ):
                    self.condicao.wait()
                if self.encerrado:
                    Log.msg(f"Cliente {cliente_id}: pedido NÃO ACEITO; "
                            "atendimento encerrado")
                    return -1

            self.respostas_na_rodada += 1
            if pedido is None:
                Log.msg(f"Cliente {cliente_id} NÃO vai pedir na rodada "
                        f"{self.rodada} do Garçom {self.id}")
            else:
                self.fila.append(pedido)
                self.senha_da_vez += 1
                Log.msg(f"Cliente {cliente_id}: PEDIDO ACEITO pelo Garçom {self.id} "
                        f"(senha {pedido.senha}, posição {len(self.fila)}/"
                        f"{self.capacidade}, rodada {self.rodada})")
            self.condicao.notify_all()
            return self.rodada

    def espera_pedido(self, pedido):
        with self.condicao:
            while not pedido.entregue and not self.encerrado:
                self.condicao.wait()
            return pedido.entregue

    def is_encerrado(self):
        with self.condicao:
            return self.encerrado

    def recebe_maximo_pedidos(self):
        with self.condicao:
            Log.msg(f"Garçom {self.id} - RODADA {self.rodada}/{self.rodadas_total} "
                    f"({len(self.fila)} pedido(s) aceito(s) na fila)")
            # Lote parcial só quando todos os clientes já responderam.
            while (not self.encerrado and len(self.fila) < self.capacidade
                   and self.respostas_na_rodada < self.total_clientes):
                self.condicao.wait()
            if self.encerrado:
                return None
            self.atendendo = True
            lote = [self.fila.popleft() for _ in range(len(self.fila))]
            if len(lote) < self.capacidade:
                Log.msg(f"Garçom {self.id}: todos já decidiram; há somente "
                        f"{len(lote)} pedido(s) nesta rodada")
            return lote

    def registra_pedidos(self, lote):
        ordem = " ".join(f"C{p.cliente_id}(senha {p.senha})" for p in lote)
        Log.msg(f"Garçom {self.id} selecionou {len(lote)}/{self.capacidade} "
                f"pedido(s): {ordem} -> indo à COPA")
        return self.bartender.preparar_pedidos(self.id, len(lote))

    def entrega_pedidos(self, lote):
        for pedido in lote:
            if self.bar.cancelado.wait(0.15):
                return False
            with self.condicao:
                if self.encerrado:
                    return False
                pedido.entregue = True
                Log.msg(f"Garçom {self.id} ENTREGOU ao Cliente {pedido.cliente_id} "
                        f"(senha {pedido.senha})")
                self.condicao.notify_all()
        return True

    def conclui_rodada(self):
        with self.condicao:
            if self.encerrado:
                return
            Log.msg(f"Garçom {self.id} concluiu a rodada {self.rodada}")
            if self.rodada == self.rodadas_total:
                self.encerrar("limite de rodadas atingido")
                return
            self.rodada += 1
            self.respostas_na_rodada = 0
            self.atendendo = False
            Log.msg(f"Garçom {self.id} LIBERADO para a rodada {self.rodada}")
            self.condicao.notify_all()

    def encerrar(self, motivo):
        with self.condicao:
            if not self.encerrado:
                self.encerrado = True
                Log.msg(f"Garçom {self.id}: atendimento encerrado ({motivo})")
                self.condicao.notify_all()

    def run(self):
        try:
            if self.total_clientes == 0:
                self.encerrar("não tem clientes atribuídos")
                return
            while not self.is_encerrado():
                lote = self.recebe_maximo_pedidos()
                if lote is None:
                    break
                if lote:
                    if not self.registra_pedidos(lote):
                        break
                    if not self.entrega_pedidos(lote):
                        break
                else:
                    Log.msg(f"Garçom {self.id}: rodada SEM PEDIDOS; não vai à copa")
                self.conclui_rodada()
        except Exception as erro:
            self.bar.cancelar(f"falha no Garçom {self.id}: {erro}")
        finally:
            self.encerrar("fim do expediente")
            Log.msg(f"Garçom {self.id} encerrou o expediente")
            self.bar.garcom_terminou()


class Cliente(Thread):
    def __init__(self, id, garcom):
        super().__init__(name=f"Cliente {id}")
        self.id = id
        self.garcom = garcom
        self.random = random.Random()

    def run(self):
        ultima_rodada = 0
        try:
            while not self.garcom.is_encerrado():
                pedido = Pedido(self.id) if self.random.randrange(100) < 70 else None
                ultima_rodada = self.garcom.faz_pedido(self.id, ultima_rodada, pedido)
                if ultima_rodada == -1:
                    break
                if pedido is None:
                    continue
                if not self.garcom.espera_pedido(pedido):
                    Log.msg(f"Cliente {self.id}: pedido de senha {pedido.senha} "
                            "não entregue; saindo sem consumir")
                    break
                self.recebe_pedido()
                self.consome_pedido()
        except Exception as erro:
            self.garcom.bar.cancelar(f"falha no Cliente {self.id}: {erro}")
        finally:
            Log.msg(f"Cliente {self.id} foi embora")

    def recebe_pedido(self):
        Log.msg(f"Cliente {self.id} RECEBEU o pedido")

    def consome_pedido(self):
        tempo = self.random.randrange(500, 2000)
        Log.msg(f"Cliente {self.id} consumindo por {tempo} ms")
        # Event.wait faz a pausa e permite encerrá-la antecipadamente com Ctrl+C.
        self.garcom.bar.cancelado.wait(tempo / 1000)


def main(argumentos=None):
    argumentos = sys.argv[1:] if argumentos is None else argumentos
    if len(argumentos) not in (0, 4):
        print("Uso: python3 bar.py <clientes> <garcons> <capacidade> <rodadas>")
        return 1
    try:
        valores = list(map(int, argumentos)) if argumentos else [8, 2, 3, 3]
    except ValueError:
        print("Todos os parâmetros devem ser números inteiros.")
        return 1
    if any(valor < 1 for valor in valores):
        print("Todos os parâmetros devem ser >= 1.")
        return 1
    clientes, garcons, capacidade, rodadas = valores
    Log.msg(f"BAR ABERTO: {clientes} clientes, {garcons} garçons, "
            f"capacidade {capacidade}, {rodadas} rodadas")

    bar = Bar(garcons)
    bartender = Bartender(bar.cancelado)
    qtd_por_garcom = [0] * garcons
    for c in range(clientes):
        qtd_por_garcom[c % garcons] += 1
    bar.garcons = [
        Garcom(g + 1, capacidade, qtd_por_garcom[g], rodadas, bartender, bar)
        for g in range(garcons)
    ]
    lista_clientes = []
    for c in range(clientes):
        garcom = bar.garcons[c % garcons]
        lista_clientes.append(Cliente(c + 1, garcom))
        Log.msg(f"Cliente {c + 1} será atendido pelo Garçom {garcom.id}")

    threads = bar.garcons + lista_clientes
    iniciadas = []
    codigo_saida = 0
    try:
        for thread in threads:
            thread.start()
            iniciadas.append(thread)
        for thread in iniciadas:
            thread.join()
    except KeyboardInterrupt:
        codigo_saida = 130
        bar.cancelar("interrupção pelo usuário")
    except Exception as erro:
        codigo_saida = 1
        bar.cancelar(f"falha ao executar as threads: {erro}")
    finally:
        for thread in iniciadas:
            thread.join()

    if bar.cancelado.is_set():
        Log.msg("Programa interrompido: os atendimentos foram cancelados.")
        return codigo_saida or 1
    Log.msg("Programa finalizado: todos os clientes saíram.")
    return 0


if __name__ == "__main__":
    sys.exit(main())