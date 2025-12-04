import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.streams.toList

class FileSystem() {
    
    /**
     * Читает содержимое файла как строку
     * @param path Путь к файлу
     * @return Содержимое файла как строка
     */
    fun read(path: String): String {
        return Files.readString(Paths.get(path))
    }
    
    /**
     * Записывает строку в файл (перезаписывает существующий файл)
     * @param path Путь к файлу
     * @param content Содержимое для записи
     */
    fun write(path: String, content: String) {
        Files.writeString(Paths.get(path), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
    }
    
    /**
     * Добавляет строку в конец файла
     * @param path Путь к файлу
     * @param content Содержимое для добавления
     */
    fun append(path: String, content: String) {
        Files.writeString(Paths.get(path), content, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
    
    /**
     * Проверяет, существует ли файл или директория
     * @param path Путь к файлу или директории
     * @return true если существует, false иначе
     */
    fun exists(path: String): Boolean {
        return Files.exists(Paths.get(path))
    }
    
    /**
     * Проверяет, является ли путь файлом
     * @param path Путь для проверки
     * @return true если это файл, false иначе
     */
    fun isFile(path: String): Boolean {
        return Files.isRegularFile(Paths.get(path))
    }
    
    /**
     * Проверяет, является ли путь директорией
     * @param path Путь для проверки
     * @return true если это директория, false иначе
     */
    fun isDir(path: String): Boolean {
        return Files.isDirectory(Paths.get(path))
    }
    
    /**
     * Создает директорию (и все необходимые родительские директории)
     * @param path Путь к директории
     */
    fun mkdir(path: String) {
        Files.createDirectories(Paths.get(path))
    }
    
    /**
     * Удаляет файл или директорию
     * @param path Путь к файлу или директории
     */
    fun delete(path: String) {
        val filePath = Paths.get(path)
        if (Files.isDirectory(filePath)) {
            Files.walk(filePath)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.delete(it) }
        } else {
            Files.delete(filePath)
        }
    }
    
    /**
     * Получает размер файла в байтах
     * @param path Путь к файлу
     * @return Размер файла в байтах (ограничен Int.MAX_VALUE)
     */
    fun size(path: String): Int {
        val fileSize = Files.size(Paths.get(path))
        return if (fileSize > Int.MAX_VALUE) Int.MAX_VALUE else fileSize.toInt()
    }
    
    /**
     * Получает список файлов и директорий в указанной директории
     * @param path Путь к директории
     * @return Массив строк с именами файлов и директорий
     */
    fun list(path: String): Array<String> {
        val dir = Paths.get(path)
        if (!Files.isDirectory(dir)) {
            return arrayOf()
        }
        return Files.list(dir)
            .map { it.fileName.toString() }
            .toList()
            .toTypedArray()
    }
    
    /**
     * Переименовывает или перемещает файл/директорию
     * @param source Исходный путь
     * @param target Целевой путь
     */
    fun move(source: String, target: String) {
        Files.move(Paths.get(source), Paths.get(target))
    }
    
    /**
     * Копирует файл
     * @param source Исходный путь
     * @param target Целевой путь
     */
    fun copy(source: String, target: String) {
        Files.copy(Paths.get(source), Paths.get(target))
    }
}

