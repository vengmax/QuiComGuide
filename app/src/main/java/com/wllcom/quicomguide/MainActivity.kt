package com.wllcom.quicomguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Scaffold
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.wllcom.quicomguide.data.source.cloud.AuthService
import com.wllcom.quicomguide.ui.navigation.AppNavHost
import com.wllcom.quicomguide.ui.theme.QuiComGuideTheme
import com.wllcom.quicomguide.ui.viewmodel.AuthViewModel
import com.wllcom.quicomguide.ui.viewmodel.MaterialsViewModel
import com.wllcom.quicomguide.ui.viewmodel.StorageViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.collectAsState

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuiComGuideTheme {
                val navController = rememberNavController()

                val viewModel: MaterialsViewModel by viewModels()
//                val isReady by viewModel.isAiSearchReady.collectAsState()
//                LaunchedEffect(isReady) {
//                    if(isReady)
//                        insertSampleData(viewModel)
//                }

                // auto signIn
                val viewModelAuth: AuthViewModel = hiltViewModel()
                viewModelAuth.signIn(silent = true)

                Scaffold { systemPadding ->
                    AppNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        systemPadding = systemPadding
                    )
                }
            }
        }
    }
}

//suspend fun insertSampleData(repo: MaterialsViewModel) {
//
//    val xml1 = """<material>
//  <title>TextView в Android</title>
//
//  <section>
//    <title>Вывод текста</title>
//      <content>
//        Элемент TextView используется для отображения текста на экране.
//        В XML вы задаёте атрибут android:text для указания содержимого.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml1)
//
//    val xml2 = """<material>
//  <title>EditText в Android</title>
//
//  <section>
//    <title>Поле ввода</title>
//      <content>
//        EditText — это элемент для ввода текста пользователем.
//        Можно указать тип ввода, например textPassword или number.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml2)
//
//    val xml3 = """<material>
//  <title>ImageView в Android</title>
//
//  <section>
//    <title>Отображение изображения</title>
//      <content>
//        Элемент ImageView позволяет показывать изображения.
//        Используйте атрибут android:src для ссылки на ресурс.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml3)
//
//    val xml4 = """<material>
//  <title>LinearLayout</title>
//
//  <section>
//    <title>Расположение элементов</title>
//      <content>
//        LinearLayout размещает элементы по горизонтали или вертикали.
//        Используйте атрибут orientation для задания направления.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml4)
//
//    val xml5 = """<material>
//  <title>ConstraintLayout</title>
//
//  <section>
//    <title>Современный контейнер</title>
//      <content>
//        ConstraintLayout — мощный контейнер для сложных интерфейсов.
//        Позволяет связывать элементы друг с другом с помощью ограничений.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml5)
//
//    val xml6 = """<material>
//  <title>Switch в Android</title>
//
//  <section>
//    <title>Переключатель</title>
//      <content>
//        Элемент Switch позволяет включать и выключать опцию.
//        В коде вы можете проверить состояние через isChecked.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml6)
//
//    val xml7 = """<material>
//  <title>CheckBox в Android</title>
//
//  <section>
//    <title>Флажок выбора</title>
//      <content>
//        CheckBox используется для выбора нескольких вариантов.
//        Состояние хранится в свойстве isChecked.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml7)
//
//    val xml8 = """<material>
//  <title>RadioButton в Android</title>
//
//  <section>
//    <title>Выбор одного варианта</title>
//      <content>
//        RadioButton используется для выбора одного варианта из группы.
//        Объединяйте их внутри RadioGroup.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml8)
//
//    val xml9 = """<material>
//  <title>ProgressBar в Android</title>
//
//  <section>
//    <title>Индикатор выполнения</title>
//      <content>
//        ProgressBar отображает ход выполнения задачи.
//        Может быть круговым или линейным.
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml9)
//
//    val xml10 = """<material>
//  <title>Toast в Android</title>
//
//  <section>
//    <title>Краткое сообщение</title>
//      <content>
//        Toast используется для показа коротких уведомлений.
//        Пример: Toast.makeText(context, "Привет", Toast.LENGTH_SHORT).show()
//      </content>
//  </section>
//</material>"""
//
//    repo.addMaterial(xml10)
//
//}