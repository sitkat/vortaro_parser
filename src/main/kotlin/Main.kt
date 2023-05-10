import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.sql.DriverManager
import java.sql.SQLException
import java.time.LocalDateTime


fun main() {
    var text = translation1.text

    val translations = text.split("\n\n")
    val failedTranslations = mutableListOf<Pair<String, String>>()

    // multiline mode enabled

    // \[[^~].+\].+\n*.+(.[.;]$) - first translation
    val firstLineTranslationRegex = Regex("\\[[^~].+\\][^\\[\\t@]+\\n*[^\\[~@]+([;.]\$)", setOf(RegexOption.MULTILINE))
    // \[[^~].+\].+\n*.+(.[.;]$| _прим._) - this but with annotation (need to drop last 8 chars)
    val firstLineTranslationWithAnnotation = Regex("\\[[^~].+\\].+\\n*.+(.[.;]\$| _прим._)")
    // \[~.+\].+\n*(.[.;]$) - subsequent simple
    val subsequentSimpleTranslationRegex = Regex("\\[~.+\\].+\\n*(.[.;]\$)")
    // @.+\n*(.[.;]$) - subsequent weird @
    val sstWeirdRegex = Regex("@.+\\n*(.[.;]\$)")

    // \[~.+\].+\d\. - first variation with determined ending
    val firstVariationWithDeterminedEndingRegex = Regex("\\[~.+\\].+\\d\\.")
    // \d\..+\n*.+[.;]$ - determined ending word variation
    val oneOfTheVariationRegex = Regex("\\d\\..+\\n*.+[.;]\$")

    // \[[^~].+\||\[[^~].+\] - root word
    val rootWordRegex = Regex("\\[[^~].+\\||\\[[^~].+\\]")
    // \|[^~].+\] - first variation of root word (optional)
    val rootWordFirstMandatoryVariationRegex = Regex("\\|[^~].+\\]")
    // (when root word has | in it basically, the root word without it is meaningless)

    val sameWordVariationRegex = Regex("\\d\\..+\\n*.+[;,.]")

    // _.+_|_.+\n.+_ - description (optional)
    val descriptionRegex = Regex("\\([^;]+\\)|_[^;]+_|\\([^;]+\\n*[^;]+\\)|_.+\\n.+_")

    // [a-zA-Z\s]+[;.,] - single translation
    val soloTranslationRegex = Regex("[a-zA-Z\\s]+[;.,]")

//    val wordVariantRegex = Regex("\\d\\.[^@\\[\\d]+\\n*[^@\\[\\d]+[;.]\$")
  
    // (_[^;]+\n*[^;]+_)([a-zA-Z\s]+[;.,]) - desc and translation 1st and 2nd group accordingly
    // (\([^;]+\n*[^;]+\))([a-zA-Z\s]+[;.,]) - desc and translation 1st and 2nd group accordingly (for brackets descriptions)

    // \[~.+\] - another ending (use with root word)
    val anotherEndingRegex = Regex("\\[~.+\\]")
    val weirdEndingRegex = Regex("@.+~`[а-яА-Я]+")

    // date regex
    val dateRegex = Regex("\\d+-\\d+-\\d+ \\d+:\\d+")

    data class Entry(
            var id: Int,
            var date: String,
            var word: String,
            var description: String?,
            var translations: MutableList<String>
    )
    var increment = 0;

    val entries = mutableListOf<Entry>()
    tr@ for (translation in translations) {
        val dates: MutableList<String?> = mutableListOf()
        var rootWord: String? = null


        for (line in translation.split("\n")) {
            // check if this line is a date
            if (!line.contains(dateRegex)) break
            // get the indexes of start and end of the date
            val dateRange = dateRegex.find(line)
            if (dateRange != null) // add the date to dates
                dates.add(line.substring(dateRange.range))
        }


        val firstTranslation = firstLineTranslationRegex.find(translation)?.value
                ?: firstLineTranslationWithAnnotation.find(translation)?.value?.dropLast(8)

        if (firstTranslation == null) {
            failedTranslations.add("Could not parse first translation" to translation)
            continue@tr
        }

        val rootWordMatched = rootWordRegex.find(firstTranslation)
        if (rootWordMatched == null) {
            failedTranslations.add("Failed to parse root word in translation" to firstTranslation)
            continue@tr
        }

        rootWord = rootWordMatched.value.substring(
                1,
                rootWordMatched.value.length - 1
        )// [hello] -> hello  // removes start and end characters
        val word: String
        if (rootWord.endsWith("|")) {
            val meaningVariationEnding = rootWordFirstMandatoryVariationRegex.find(firstTranslation)?.value
            if (meaningVariationEnding == null) {
                failedTranslations.add("Could not parse meaning variation" to firstTranslation)
                continue@tr
            }

            word = rootWord + meaningVariationEnding
        } else word = rootWord
        val description = descriptionRegex.find(firstTranslation)?.value
        val wordTranslations = mutableListOf<String>()

        val translationsMatched = soloTranslationRegex.findAll(firstTranslation)
        for (thisTranslation in translationsMatched) {
            val matchedTranslation = thisTranslation.value.trim().dropLast(1)
            wordTranslations.add(matchedTranslation)
        }
        increment += 1
        entries.add(Entry(increment,dates.firstOrNull() ?: LocalDateTime.now().toString(), word, description?.replace("_", "")?.replace("(", "")?.replace(")", ""), wordTranslations))

        val subsequentSimpleTranslations = subsequentSimpleTranslationRegex.findAll(translation)
        for (sst in subsequentSimpleTranslations) {

            val endingMatchedValue = anotherEndingRegex.find(sst.value)?.value
            if (endingMatchedValue == null) {
                failedTranslations.add("Failed to get ending from sst" to sst.value)
                continue@tr
            }

            val ending = endingMatchedValue.substring(2, endingMatchedValue.length - 1)

            val word = rootWord + ending
            val description = descriptionRegex.find(sst.value)?.value
            val wordTranslations = mutableListOf<String>()

            val translationsMatched = soloTranslationRegex.findAll(sst.value)
            for (thisTranslation in translationsMatched) {
                val matchedTranslation = thisTranslation.value.trim().dropLast(1)
                wordTranslations.add(matchedTranslation)
            }
            increment += 1
            entries.add(Entry(increment,dates.firstOrNull() ?: LocalDateTime.now().toString(), word, description?.replace("_", "")?.replace("(", "")?.replace(")", ""), wordTranslations))
        }

        val subsequentSimpleWeirdTranslations = sstWeirdRegex.findAll(translation)
        for (sstw in subsequentSimpleWeirdTranslations) {
            val endingMatchedValue = weirdEndingRegex.find(sstw.value)?.value
            if (endingMatchedValue == null) {
                failedTranslations.add("Failed to get ending from sstw" to sstw.value)
                continue@tr
            }

            val ending = endingMatchedValue.substringAfter('`')   //.substring(2, endingMatchedValue.length - 1)

            val word = rootWord + ending
            val description = descriptionRegex.find(sstw.value)?.value
            val wordTranslations = mutableListOf<String>()

            val translationsMatched = soloTranslationRegex.findAll(sstw.value)
            for (thisTranslation in translationsMatched) {
                val matchedTranslation = thisTranslation.value.trim().dropLast(1)
                wordTranslations.add(matchedTranslation)
            }
            increment += 1
            entries.add(Entry(increment,dates.firstOrNull() ?: LocalDateTime.now().toString(), word, description, wordTranslations))
        }
    }
//    println(entries)
//    println(failedTranslations)


    val connectionNotAuth = DriverManager.getConnection(
            "jdbc:sqlite:C:\\Users\\sitka\\Desktop\\dictparser\\src\\main\\assets\\dbMain.db")

    val connectionAuth = DriverManager.getConnection(
            "jdbc:sqlite:C:\\Users\\sitka\\Desktop\\dictparser\\src\\main\\assets\\dbTest.db")

    for (o in entries) {

        val sql = """
        INSERT INTO Word (edition, title, translation, description) VALUES ('${o.date.replace("'","''")}','' ||
                                                                                   '${o.word.replace("'","''").replace("(", "").replace(")", "").replace("<<", "").replace(">>", "")}',
                                                                                   '${if (o.translations.isNotEmpty()) o.translations.reduce { acc, s -> acc + s + "; " }.trim().replace("'","''").replace("(", "").replace(")", "").replace("<<", "").replace(">>", "") else ""}',
                                                                                   '${o.description?.replace("'","''")}')
    """.trimIndent()
        val statement = connectionNotAuth.createStatement()
        statement.execute(sql)


//        var user_id = 1
//        val sqlNew = """
//        INSERT INTO _word (id, edition, title, translation, description, user_id) VALUES ('${o.id}','${o.date.replace("'","''")}',
//'${o.word.replace("'","''").replace("(", "").replace(")", "").replace("<<", "").replace(">>", "")}',
//'${if (o.translations.isNotEmpty()) o.translations.reduce { acc, s -> acc + s + "; " }.trim().replace("'","''").replace("(", "").replace(")", "").replace("<<", "").replace(">>", "") else ""}',
//'${o.description?.replace("'","''")}', '${user_id}')
//    """.trimIndent()
//        val statement = connectionAuth.createStatement()
//        statement.execute(sqlNew)

    }
}

