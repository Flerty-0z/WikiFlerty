package com.flerty.wikiflerty.ai

/** What the user asked the AI to do with the selected text. */
enum class AiAction(val id: String, val title: String, val hint: String, val mark: String) {
    Explain("explain", "Объяснить", "Просто, с учётом текста вокруг", "?"),
    Define("define", "Определение", "Как в толковом словаре", "Аб"),
    Translate("translate", "Перевод", "На выбранный язык", "En"),
    Ask("ask", "Свой вопрос", "Что угодно об отрывке", "…"),
}

/** Builds the questions sent to the model. */
object AiPrompts {

    fun system(bookTitle: String, author: String, settings: AiSettings): String = buildString {
        append("Ты — умный помощник внутри читалки электронных книг. ")
        append("Пользователь читает книгу «$bookTitle»")
        if (author.isNotBlank()) append(" ($author)")
        append(". Отвечай по-русски, понятно и доходчиво, как хороший учитель, без воды и без вступлений вроде «Конечно!». ")
        if (settings.style == "detailed") {
            append("Давай развёрнутые ответы с примерами. ")
        } else {
            append("Отвечай коротко: обычно 2–5 предложений. ")
        }
        append("Можно использовать **жирный** для ключевых слов и короткие списки через «- ». Не используй таблицы и заголовки.")
    }

    /** The first message of a conversation about [selected], with the text around it as [context]. */
    fun first(action: AiAction, selected: String, context: String, settings: AiSettings, question: String? = null): String {
        val fragment = selected.trim()
        val around = context.trim().takeIf { it.isNotEmpty() && it != fragment }
        val contextLine = around?.let { "\n\nКонтекст из книги: «$it»" }.orEmpty()
        return when (action) {
            AiAction.Explain ->
                "Объясни простыми словами, что значит «$fragment» в этом месте книги. " +
                    "Сначала смысл именно здесь, потом коротко общее значение, если оно отличается.$contextLine"
            AiAction.Define ->
                "Дай словарное определение для «$fragment»: часть речи, основное значение " +
                    "(и другие, если важны), короткий пример употребления. Отметь, какое значение использовано в книге.$contextLine"
            AiAction.Translate ->
                "Переведи «$fragment» на ${settings.translateTo}. Если это слово или выражение — дай 2–3 варианта перевода " +
                    "и укажи, какой подходит здесь. Если это фраза — дай естественный перевод.$contextLine"
            AiAction.Ask ->
                "Вопрос об отрывке «$fragment»: ${question.orEmpty().trim()}$contextLine"
        }
    }

    /** Ready-made follow-up requests under an answer. */
    val followUps = listOf(
        "Проще" to "Объясни ещё проще, как для школьника.",
        "Примеры" to "Приведи 2–3 коротких примера употребления.",
        "Синонимы" to "Какие есть синонимы и чем они отличаются по оттенку?",
        "Перевод" to "Как это будет по-английски? Дай варианты.",
    )
}
