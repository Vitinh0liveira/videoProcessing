
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;

public class VideoProcessing {

    /*
     * Carrega a biblioteca nativa (via nu.pattern.OpenCV) assim que a classe é
     * carregada na VM.
     */
    static {
        nu.pattern.OpenCV.loadLocally();
    }

    public static byte[][][] carregarVideo(String caminho) {

        VideoCapture captura = new VideoCapture(caminho);
        if (!captura.isOpened()) {
            System.out.println("Vídeo está sendo processado por outra aplicação");
        }

        // tamanho do frame
        int largura = (int) captura.get(Videoio.CAP_PROP_FRAME_WIDTH);
        int altura = (int) captura.get(Videoio.CAP_PROP_FRAME_HEIGHT);

        // não conhecço a quantidade dos frames (melhorar com outra lib) :(
        List<byte[][]> frames = new ArrayList<>();

        // matriz RGB mesmo preto e branco?? - uso na leitura do frame
        Mat matrizRGB = new Mat();

        // criando uma matriz temporária em escala de cinza
        Mat escalaCinza = new Mat(altura, largura, CvType.CV_8UC1); // 1 única escala
        byte linha[] = new byte[largura];

        while (captura.read(matrizRGB)) {// leitura até o último frames

            // convertemos o frame atual para escala de cinza
            Imgproc.cvtColor(matrizRGB, escalaCinza, Imgproc.COLOR_BGR2GRAY);

            // criamos uma matriz para armazenar o valor de cada pixel (int estouro de
            // memória)
            byte pixels[][] = new byte[altura][largura];
            for (int y = 0; y < altura; y++) {
                escalaCinza.get(y, 0, linha);
                for (int x = 0; x < largura; x++) {
                    pixels[y][x] = (byte) (linha[x] & 0xFF); // shift de correção - unsig
                }
            }
            frames.add(pixels);
        }
        captura.release();

        /* converte o array de frames em matriz 3D */
        byte cuboPixels[][][] = new byte[frames.size()][][];
        for (int i = 0; i < frames.size(); i++) {
            cuboPixels[i] = frames.get(i);
        }

        return cuboPixels;
    }

    public static void gravarVideo(byte pixels[][][],
            String caminho,
            double fps) {

        int qFrames = pixels.length;
        int altura = pixels[0].length;
        int largura = pixels[0][0].length;

        int fourcc = VideoWriter.fourcc('a', 'v', 'c', '1'); // identificação codec .mp4
        VideoWriter escritor = new VideoWriter(
                caminho, fourcc, fps, new Size(largura, altura), true);

        if (!escritor.isOpened()) {
            System.err.println("Erro ao gravar vídeo no caminho sugerido");
        }

        Mat matrizRgb = new Mat(altura, largura, CvType.CV_8UC3); // voltamos a operar no RGB (limitação da lib)

        byte linha[] = new byte[largura * 3]; // BGR intercalado

        for (int f = 0; f < qFrames; f++) {
            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < largura; x++) {
                    byte g = (byte) pixels[f][y][x];
                    int i = x * 3;
                    linha[i] = linha[i + 1] = linha[i + 2] = g; // cinza → B,G,R
                }
                matrizRgb.put(y, 0, linha);
            }
            escritor.write(matrizRgb);
        }
        escritor.release(); // limpando o buffer
    }

    public static byte[][][] removerSalPimenta(byte[][][] frames) {
        int qframes = frames.length;
        int altura = frames[0].length;
        int largura = frames[0][0].length;

        byte[][][] novoVideo = new byte[qframes][altura][largura];
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int f = 0; f < qframes; f++) {
            final int frameIndex = f;

            executor.submit(() -> {
                for (int i = 0; i < altura; i++) {
                    for (int j = 0; j < largura; j++) {
                        int valorAtual = frames[frameIndex][i][j] & 0xFF;

                        if (valorAtual == 0 || valorAtual == 255) {
                            int somaVizinhos = 0;
                            int contadorVizinhos = 0;
                            int contadorPretos = 0;

                            for (int di = -1; di <= 1; di++) {
                                for (int dj = -1; dj <= 1; dj++) {
                                    int ni = i + di;
                                    int nj = j + dj;

                                    if (ni >= 0 && ni < altura && nj >= 0 && nj < largura) {
                                        if (!(di == 0 && dj == 0)) {
                                            int valorVizinho = frames[frameIndex][ni][nj] & 0xFF;
                                            somaVizinhos += valorVizinho;
                                            contadorVizinhos++;
                                            if (valorVizinho == 0)
                                                contadorPretos++;
                                        }
                                    }
                                }
                            }

                            if (contadorPretos > contadorVizinhos / 2) {
                                novoVideo[frameIndex][i][j] = 0;
                            } else {
                                if (contadorVizinhos > 0) {
                                    novoVideo[frameIndex][i][j] = (byte) (somaVizinhos / contadorVizinhos);
                                } else {
                                    novoVideo[frameIndex][i][j] = (byte) valorAtual;
                                }
                            }
                        } else {
                            novoVideo[frameIndex][i][j] = (byte) valorAtual;
                        }
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return novoVideo;
    }

    public static void removerBorroesTempo(byte[][][] frames) {
        int qframes = frames.length;
        int altura = frames[0].length;
        int largura = frames[0][0].length;

        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int f = 2; f < qframes - 2; f++) {
            final int frameIndex = f;

            executor.submit(() -> {
                for (int i = 0; i < altura; i++) {
                    for (int j = 0; j < largura; j++) {
                        int[] valores = new int[5];
                        for (int k = -2; k <= 2; k++) {
                            valores[k + 2] = frames[frameIndex + k][i][j] & 0xFF;
                        }
                        Arrays.sort(valores);
                        int mediana = valores[2];
                        frames[frameIndex][i][j] = (byte) mediana;
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        String caminhoVideo = "C:\\Users\\voliv\\OneDrive\\AOC\\video.mp4";
        String caminhoGravar = "C:\\Users\\voliv\\OneDrive\\AOC\\video2.mp4";
        double fps = 21.0; // isso deve mudar se for outro vídeo (avaliar metadados ???)

        System.out.println("Carregando o vídeo... " + caminhoVideo);
        byte pixels[][][] = carregarVideo(caminhoVideo);

        System.out.printf("Frames: %d   Resolução: %d x %d \n",
                pixels.length, pixels[0][0].length, pixels[0].length);

        System.out.println("processamento remove ruído 1");
        pixels = removerSalPimenta(pixels);

        System.out.println("processamento remove ruído 2");
        removerBorroesTempo(pixels);

        System.out.println("Salvando...  " + caminhoGravar);
        gravarVideo(pixels, caminhoGravar, fps);
        System.out.println("Término do processamento");
    }
}
