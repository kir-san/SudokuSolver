package com.san.kir.sudokusolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


class SudokuSolver {

    SudokuSolver(String task) {
        sudoku = convertToSudoku(task);
    }

    private boolean notDecideSudoku = false;
    private boolean problemsInSudoku = false;
    List<List<List<Integer>>> sudoku;
    private boolean hasSuccessClean = false;

    // Преобразование строки с заданием в многомерный список
    static List<List<List<Integer>>> convertToSudoku(String task) {
        List<List<List<Integer>>> temp = new ArrayList<>();
        int count = 0;
        for (int x = 0; x < 9; x++) {
            List<List<Integer>> list = new ArrayList<>();
            for (int y = 0; y < 9; y++) {
                char cell = task.charAt(count++);
                list.add(sToA(cell));
            }
            temp.add(list);
        }
        return temp;
    }

    // Замена строки с цифрой на список
    private static List<Integer> sToA(char sim) {
        List<Integer> list = new ArrayList<>();
        switch (sim) {
            case '1':
                list.add(1);
                break;
            case '2':
                list.add(2);
                break;
            case '3':
                list.add(3);
                break;
            case '4':
                list.add(4);
                break;
            case '5':
                list.add(5);
                break;
            case '6':
                list.add(6);
                break;
            case '7':
                list.add(7);
                break;
            case '8':
                list.add(8);
                break;
            case '9':
                list.add(9);
                break;
            default:
                for (int i = 1; i <= 9; i++) {
                    list.add(i);
                }
                break;

        }
        return list;
    }

    // Главная функция, решающая головоломку
    boolean decideSudoku() {
        // Если решение было подобрано, то завершить выполнение
        if (decideAllPartSudoku()) return true;
        // Если во время решения произошла ошибка, то завершить выполнение
        if (problemsInSudoku) return false;
        // Если головоломка не решена
        if (notDecideSudoku) {
            // Находим первую клетку с наименьшим количеством значений
            SaveSudoku varMin = lessUnfamousCell();
            // Пробуем решить судоку, подставляя в найденую клетку одно из ее значений
            for (int num : varMin.values) {
                List<Integer> tem = new ArrayList<>();
                tem.add(num);
                sudoku.get(varMin.coorRow).set(varMin.coorCol, tem);
                problemsInSudoku = false;

                if (!decideSudoku()) {
                    sudoku = varMin.sudoku;
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    // Функция совмещающая решение головоломки в трех направлениях
    private boolean decideAllPartSudoku() {
        // Решаем построчно
        Result resultRow = decidePartSudoku(sudoku);
        sudoku = resultRow.value;

        // Проверяем решена ли она на данном этапе и есть ли проблемы
        if (hasDecideSudoku(resultRow.value)) return true;
        if (problemsInSudoku) return false;

        // Решаем по столбцам
        Result resultCol = decidePartSudoku(rowsToCols(resultRow.value));
        sudoku = rowsToCols(resultCol.value);

        // Проверяем решена ли она на данном этапе и есть ли проблемы
        if (hasDecideSudoku(resultCol.value)) return true;
        if (problemsInSudoku) return false;

        // Решаем по квадратам
        Result resultSqus = decidePartSudoku(colsToSqus(resultCol.value));
        sudoku = squsToRows(resultSqus.value);

        // Проверяем решена ли она на данном этапе
        if (hasDecideSudoku(resultSqus.value)) return true;

        // Если была найдена хоть одна новая цифра, то решаем еще раз
        boolean resultChecking = resultRow.status || resultCol.status || resultSqus.status;
        if (!resultChecking) {
            notDecideSudoku = true;
            return false;
        }

        return decideAllPartSudoku();
    }

    // Решение головоломки в одном направлении
    private Result decidePartSudoku(List<List<List<Integer>>> part) {
        // хранение статуса о нахождении хоть одной новой цифры
        boolean hasNew = false;

        for (List<List<Integer>> cells : part) {
            // Если головоломка не корректная, то ставим флаг о проблеме и завершаем решение
            if (checkForCorrectSudoku(part, cells)) {
                problemsInSudoku = true;
                return new Result(false, part);
            }

            // Обнуляем статус удачной чистки и чистим списки от известных цифр
            hasSuccessClean = false;
            List<List<Integer>> temp = cleanOfFamousNumbers(cells);

            // Добавляем ХэшТаблицу и заносим в нее все неизвестные цифры и обнуляем счетчики для них
            HashMap<Integer, Integer> counter = new HashMap<>();
            List<Integer> famousNumbers = famousNumbers(temp);
            for (int i = 1; i <= 9; i++) {
                if (!famousNumbers.contains(i))
                    counter.put(i, 0);
            }

            // Обнуляем статус о нахождении новой известной цифры
            boolean hasNewFromUnFamous = false;

            // Если Таблица не пустая
            if (!counter.isEmpty()) {
                // Если в клетке неизвестное число,
                // то для предпологаемых чисел увеличиваем соответсвующие счетчики
                for (List<Integer> value : temp)
                    if (value.size() > 1)
                        for (int i : value)
                            if (counter.containsKey(i))
                                counter.put(i, counter.get(i) + 1);
                // Проверяем каждый счетчик
                for (Map.Entry<Integer, Integer> entry : counter.entrySet()) {
                    // Если какой-либо счетчик равен одному,
                    // то цифра к которой относится счетчик - известная
                    if (entry.getValue() == 1) {
                        for (int inter = 0; inter < 9; inter++) {
                            // Находим в какой клетке ее нашли, сохраняем и меняем статус
                            if (temp.get(inter).contains(entry.getKey())) {
                                List<Integer> list = new ArrayList<>();
                                list.add(entry.getKey());
                                temp.set(inter, list);
                                hasNewFromUnFamous = true;
                                break;
                            }
                        }
                    }
                }
            }
            // сохраняем промежуточные статусы в основной
            hasNew = hasNew || hasSuccessClean || hasNewFromUnFamous;
        }
        return new Result(hasNew, part);
    }

    // Проверка на корректность головоломки
    private boolean checkForCorrectSudoku(List<List<List<Integer>>> sudoku, List<List<Integer>> checkedList) {
        // Проверяем, что нет пустых клеток
        for (List<List<Integer>> collection : sudoku) {
            for (List<Integer> cell : collection) {
                if (cell.isEmpty()) return true;
            }
        }

        // Проверяем, что известные цифры не повторяются
        List<Integer> famousNumbers = famousNumbers(checkedList);
        for (int x = 0; x < famousNumbers.size() - 2; x++) {
            if (Objects.equals(famousNumbers.get(x), famousNumbers.get(x + 1))) return true;
        }
        return false;
    }

    // Поиск известных цифр в строке
    private List<Integer> famousNumbers(List<List<Integer>> checkedList) {
        List<Integer> famousNumbers = new ArrayList<>();
        // Если в списке только одна цифра, то это она известна и добавляется отдельный список
        for (List<Integer> num : checkedList) {
            if (num.size() == 1) famousNumbers.add(num.get(0));
        }
        // Сортируем полученный список
        Collections.sort(famousNumbers);

        return famousNumbers;
    }

    // Очистка списков с предполагаемыми значения от известных цифр
    private List<List<Integer>> cleanOfFamousNumbers(List<List<Integer>> checked) {
        // Находим известные цифр
        List<Integer> famousNumbers = famousNumbers(checked);
        // Обнуляем статус о нахождении новых цифр
        boolean hasNew = false;
        // производим очистку
        for (List<Integer> cells : checked) {
            if (cells.size() > 1) {
                for (Integer value : famousNumbers) {
                    cells.remove(value);
                }
                // Если новая цифра появилась, то меняем статус
                if (cells.size() == 1) hasNew = true;
            }
        }
        // Если новых цифр нет, то заканчиваем работу функции
        if (!hasNew) return checked;
        // Иначе обновляем глобальный статус о новых известных цифрах и чистим еще раз
        hasSuccessClean = true;
        return cleanOfFamousNumbers(checked);
    }

    // Проверка решена ли головоломка
    private boolean hasDecideSudoku(List<List<List<Integer>>> sudoku) {
        // Как только находим первую клетку без известной цифр, то головоломка не решена
        for (List<List<Integer>> cells : sudoku) {
            for (List<Integer> cell : cells) {
                if (cell.size() > 1) return false;
            }
        }
        return true;
    }

    // Трансформация головоломки, чтобы строки стали столбцами
    private List<List<List<Integer>>> rowsToCols(List<List<List<Integer>>> rows) {
        List<List<List<Integer>>> temp = new ArrayList<>();
        for (int column = 0; column < 9; column++) {
            List<List<Integer>> list = new ArrayList<>();
            for (int row = 0; row < 9; row++)
                list.add(rows.get(row).get(column));
            temp.add(list);
        }
        return temp;
    }

    // Трансформация головоломки, чтобы столбцы стали квадратами
    private List<List<List<Integer>>> colsToSqus(List<List<List<Integer>>> cols) {
        List<List<List<Integer>>> temp = new ArrayList<>();
        int[] rang = {0, 3, 6};
        for (int x : rang)
            for (int y : rang) {
                List<List<Integer>> list = new ArrayList<>();
                for (int row = x; row <= x + 2; row++)
                    for (int col = y; col <= y + 2; col++)
                        list.add(cols.get(col).get(row));
                temp.add(list);
            }
        return temp;
    }

    // Трансформация головоломки, чтобы квадраты стали строками
    private List<List<List<Integer>>> squsToRows(List<List<List<Integer>>> squs) {
        List<List<List<Integer>>> temp = new ArrayList<>();
        int[] rang = {0, 3, 6};
        for (int x : rang)
            for (int y : rang) {
                List<List<Integer>> list = new ArrayList<>();
                for (int row = x; row <= x + 2; row++)
                    for (int col = y; col <= y + 2; col++)
                        list.add(squs.get(row).get(col));
                temp.add(list);
            }
        return temp;
    }

    // Нахождение первой клетки с наименьшим количеством значений
    private SaveSudoku lessUnfamousCell() {
        SaveSudoku varMin = new SaveSudoku(0, 0, Arrays.asList(new Integer[10]), copySudoku());
        for (int row = 0; row < 9; row++)
            for (int col = 0; col < 9; col++) {
                // Если сохранена клетка с двумя значениями, то дальше искать бесполезно
                if (varMin.values.size() == 2) break;

                int size = sudoku.get(row).get(col).size();
                // Если количесвтво значений в клетке больше одного и меньше сохраненной клетки,
                // перезаписываем клетку
                if (size > 1 && size < varMin.values.size()) {
                    varMin.coorRow = row;
                    varMin.coorCol = col;
                    varMin.values = sudoku.get(row).get(col);
                }
            }
        return varMin;
    }

    // Полное копирование головоломки
    private List<List<List<Integer>>> copySudoku() {
        List<List<List<Integer>>> temp = new ArrayList<>();
        for (List<List<Integer>> row : sudoku) {
            List<List<Integer>> newRow = new ArrayList<>();
            for (List<Integer> cell : row) {
                List<Integer> newCell = new ArrayList<>(Collections.nCopies(cell.size(), 0));
                Collections.copy(newCell, cell);
                newRow.add(newCell);
            }
            temp.add(newRow);
        }
        return temp;
    }
}

// Класс для передачи промежуточного состояния
class Result {
    Result(boolean status, List<List<List<Integer>>> value) {
        this.status = status;
        this.value = value;
    }

    boolean status;
    List<List<List<Integer>>> value;
}

// Промежуточное хранение судоку и клетки с координатами и значениями
class SaveSudoku {
    SaveSudoku(int coorRow,
               int coorCol,
               List<Integer> values,
               List<List<List<Integer>>> sudoku) {
        this.coorRow = coorRow;
        this.coorCol = coorCol;
        this.values = values;
        this.sudoku = sudoku;
    }

    int coorRow;
    int coorCol;
    List<Integer> values;
    List<List<List<Integer>>> sudoku;
}

