package com.san.kir.sudokusolver;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.san.kir.sudokusolver.databinding.ActivityMainBinding;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    // Проверка было ли получено разрешение на использование камеры
    private final ActivityResultLauncher<String> resultLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (Boolean.TRUE.equals(isGranted)) {
                    performAction();
                } else {
                    Toast.makeText(this, "Без этого разрешения приложение не будет работать", Toast.LENGTH_SHORT).show();
                }
            });

    // Реагирование на загрузку компонентов машинного зрения
    private final BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            if (status == SUCCESS) {
                // При успешной загрузке инициализируются свойства и активируется камера
                temp = new Mat();
                hov = new Mat();
                approxCurve = new MatOfPoint2f();
                binding.camera.enableView();
            } else {
                super.onManagerConnected(status);
            }
        }
    };


    private Mat temp;
    private Mat hov;
    private MatOfPoint2f approxCurve;
    private TextRecognizer detector;
    private ActivityMainBinding binding;
    private int count = 0;
    private final List<Point> points2 = Arrays.asList(new Point(0, 0), new Point(0, 1152), new Point(1152, 1152), new Point(1152, 0));
    private final Handler mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Устанавливаем флаг, чтобы экран не гас
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Активируем пользовательский интерфейс
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Запрашиваем у пользователя разрешение на использование камеры
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            performAction();
        } else {
            resultLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void performAction() {
        // Включаем отображение камеры
        binding.camera.setVisibility(View.VISIBLE);
        binding.camera.setCvCameraViewListener(this);
        // Получаем клиента распознования текста
        detector = TextRecognition.getClient();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Отключение камеры при переходе приложения в состояние паузы
        binding.camera.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // При возращении приложения из состояния паузы, необходимо инициализировать библиотку машинного зрения
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(getApplicationContext(), "Здесь проблема", Toast.LENGTH_SHORT).show();
        } else {
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Отключение камеры, если приложение было закрыто
        binding.camera.disableView();
    }

    // Функция выполняемая при нажатии кнопки "Переснять"
    public void recapture(View view) {
        // Включаем камеру и ее отображение
        binding.camera.setVisibility(View.VISIBLE);
        binding.camera.enableView();
        // Скрываем изображение, прогрессбар и кнопку
        binding.image.setVisibility(View.INVISIBLE);
        binding.progressBar.setVisibility(View.INVISIBLE);
        binding.recapture.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }


    // Выполнение обработки изображения каждый кадр камеры
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // Из кадра камеры, получаем цветное отображение в удобном для работы библиотеке формате
        Mat dst = inputFrame.rgba();
        // Сохраняем копию
        Mat copyDst = dst.clone();

        // Преобразуем цветное в черно-белое с оттенками серого для упрощения расчетов
        Imgproc.cvtColor(dst, temp, Imgproc.COLOR_BGR2GRAY);
        // Выделение только прямых линий
        Imgproc.Canny(temp, temp, 150, 255);
        // Размывание изображения
        Imgproc.GaussianBlur(temp, temp, new Size(5, 5), 0);

        // Поиск и сохранение всех замкнутых контуров в список
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(temp, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < contours.size(); i++) {
            // Выделяем кривую из списка контуров
            MatOfPoint2f curve = new MatOfPoint2f(contours.get(i).toArray());

            // Нахождение координат каждого контура
            Imgproc.approxPolyDP(curve, approxCurve, 0.02 * Imgproc.arcLength(curve, true), true);
            Point[] points = approxCurve.toArray();

            // Нахождение площади контура
            double contourArea = Imgproc.contourArea(contours.get(i));
            // Поиск только тех контуров, которые являются четырехугольниками и площадь больше 3000
            if (points.length == 4 && contourArea > 3000) {
                // Сохраняем индекс контура в отдельный список
                indexes.add(i);

                Scalar color = new Scalar(256, 0, 0);
                // Отрисовываем найденные контуры красным цветом
                Imgproc.drawContours(dst, contours, i, color, 4, Imgproc.LINE_8, hov, 0, new Point());
            }
        }

        // Если был найден только один контур, то продолжаем работу
        if (indexes.size() == 1) {
            int i = indexes.get(0);

            Scalar color = new Scalar(0, 256, 0);
            // Отрисовать контур зеленым цветом
            Imgproc.drawContours(dst, contours, i, color, 5, Imgproc.LINE_8, hov, 0, new Point());

            // Увеличиваем счетчик, пока не станет больше 5
            count++;
            if (count > 5) {
                // Получаем кривую из списка контуров
                MatOfPoint2f curve = new MatOfPoint2f(contours.get(i).toArray());

                // Нахождение координат каждого контура
                Imgproc.approxPolyDP(curve, approxCurve, 0.02 * Imgproc.arcLength(curve, true), true);
                Point[] points = approxCurve.toArray();
                // Сортировка точек в правильном порядке
                points = reorder(points);

                Mat matPoints = Converters.vector_Point2f_to_Mat(Arrays.asList(points));
                Mat matPoints2 = Converters.vector_Point2f_to_Mat(points2);
                // Получение матрицы для трансформации изображения
                Mat pMat = Imgproc.getPerspectiveTransform(matPoints, matPoints2);
                // Создание материала с заданными размерами для сохранения части из исходного изображения
                Mat outputMat = new Mat(1152, 1152, CvType.CV_8UC4);
                // Сохранения части из исходного изображения согласно матрице преобразования
                Imgproc.warpPerspective(copyDst, outputMat, pMat, new Size(1152, 1152), Imgproc.INTER_CUBIC);

                // Создание изображения для удобной дальнейшей работы и сохранение в него
                Bitmap bm = Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(outputMat, bm);

                // Действия с пользовательским интерфейсом должны выполнять в главном потоке
                mainThreadHandler.post(() -> {
                    // Устновка изображения и отображение элемента
                    binding.image.setImageBitmap(bm);
                    binding.image.setVisibility(View.VISIBLE);
                    // Отображение кнопки "Переснять" и прогрессбара
                    binding.recapture.setVisibility(View.VISIBLE);
                    binding.progressBar.setVisibility(View.VISIBLE);
                    // Выключение камеры и скрытие из UI
                    binding.camera.disableView();
                    binding.camera.setVisibility(View.INVISIBLE);

                    // Действия по разспознаванию цифр и решение головоломки выполняем в отдельном потоке,
                    // чтобы пользовательский интерфейс не завис
                    executorService.execute(() -> {
                        // Резрезаем изображение на 81 кусочек
                        ArrayList<Bitmap> bitmaps = splitImages(bm);
                        StringBuilder builder = new StringBuilder();

                        for (Bitmap b : bitmaps) {
                            // Преобразуем изображение в удобный формат для распознания
                            InputImage image = InputImage.fromBitmap(b, 0);
                            // И запускаем процесс распознования
                            Task<Text> task = detector.process(image);

                            // Добавляем слушатель для обработки резултата
                            task.addOnCompleteListener(executorService, text -> {
                                // Из результата получаем текст
                                String number = text.getResult().getText().trim();
                                // Проверяем, что он не пуст, иначе присваиваем нулевое значение
                                if (number.length() != 1) {
                                    number = "0";
                                }
                                // Сохраняем результат
                                builder.append(number);

                                // Ждем пока не накопится 81 значение
                                if (builder.length() == 81) {
                                    // Создаем класс решателя головоломки и отдаем ему распознаные значения
                                    SudokuSolver sudokuSolver = new SudokuSolver(builder.toString());

                                    // Результат решения будем выводить, рисуя поверх исходного изображения
                                    Canvas canvas = new Canvas(bm);
                                    // Создаем кисть, настраиваем цвет и размер текста
                                    Paint paint = new Paint();
                                    paint.setColor(Color.BLACK);
                                    paint.setTextAlign(Paint.Align.CENTER);
                                    paint.setTextSize(84f);

                                    // Запускаем процесс решения гловоломки
                                    sudokuSolver.decideSudoku();

                                    for (int col = 0; col < 9; col++) {
                                        for (int row = 0; row < 9; row++) {
                                            int value = sudokuSolver.sudoku.get(row).get(col).get(0);
                                            // Рисуем значения
                                            canvas.drawText(String.valueOf(value), 0, 1, col * 128 + 64, row * 128 + 100, paint);
                                        }
                                    }
                                    mainThreadHandler.post(() -> {
                                        // В главном потоке скрывем прогрессбар и устанавливаем новое изображение
                                        binding.progressBar.setVisibility(View.INVISIBLE);
                                        binding.image.setImageBitmap(bm);
                                    });
                                }
                            });
                        }
                    });

                });
            }
        } else {
            count = 0;
        }

        return dst;
    }

    // Разделение изображения на части
    private ArrayList<Bitmap> splitImages(Bitmap bitmap) {
        // Список для хранения частей изображения
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        // Вычисляем размеры одной части изображения
        int width = bitmap.getWidth() / 9;
        int height = bitmap.getHeight() / 9;

        // Нарезаем и сохраняем
        for (int x = 0; x < 9; ++x) {
            for (int y = 0; y < 9; ++y) {
                bitmaps.add(Bitmap.createBitmap(bitmap, y * width, x * height, width, height));
            }
        }

        return bitmaps;
    }

    // Соортировка координат в правильном порядке
    private Point[] reorder(Point[] points) {
        Point[] temp = points.clone();
        List<Point> n = new ArrayList(Arrays.asList(points));

        // Находим длины векторов для каждой точки
        List<Integer> integerStream = new ArrayList<>();
        for (Point p : n) {
            Integer sqrt = (int) Math.sqrt(p.x * p.x + p.y * p.y);
            integerStream.add(sqrt);
        }

        // Коппируем список и сортируем
        List<Integer> l = new ArrayList<>(integerStream);
        Collections.sort(l);

        // Получаем индекс координат с наименьшей длинной вектора
        int i = integerStream.indexOf(l.get(0));
        // Сохраняем в последнию позицию и удаляем из исходного
        temp[3] = points[i];
        n.remove(points[i]);

        // Получаем индекс координат с наибольшей длинной вектора
        i = integerStream.indexOf(l.get(3));
        // Сохраняем во вторую позицию и удаляем из исходного
        temp[1] = points[i];
        n.remove(points[i]);

        // Определяем точку у которой значение оси х больше
        // Эту точку сохраняем в третью позицию
        // Оставшуюся сохраняем в первую позицию
        if (n.get(0).x > n.get(1).x) {
            temp[2] = n.get(0);
            temp[0] = n.get(1);
        } else {
            temp[2] = n.get(1);
            temp[0] = n.get(0);
        }

        return temp;
    }
}
