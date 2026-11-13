package at.htl.gibagym.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.model.AppSettings

/**
 * Erste Frage beim Start: nur Name, Logo und eine große Schaltfläche.
 *
 * Der Name steht danach in der Titelleiste. Mehr Felder würden hier nur vom
 * Tippen ablenken.
 */
@Composable
fun NameScreen(
    modifier: Modifier = Modifier,
    anfangsname: String = "",
    onWeiter: (String) -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf(anfangsname) }
    val name = AppSettings.nameBereinigen(text)
    val bereit = name.isNotEmpty()

    fun speichern() {
        if (bereit) onWeiter(name)
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Markenlogo(
                modifier = Modifier
                    .width(240.dp)
                    .padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_name_frage),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { neu ->
                    if (neu.length <= AppSettings.NAME_MAX_LAENGE) text = neu
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { speichern() }),
            )
            Button(
                onClick = ::speichern,
                enabled = bereit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_weiter),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
