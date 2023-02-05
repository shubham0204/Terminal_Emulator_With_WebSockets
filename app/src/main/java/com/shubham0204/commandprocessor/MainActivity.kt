package com.shubham0204.commandprocessor

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import com.shubham0204.commandprocessor.ui.theme.CommandProcessorTheme
import com.shubham0204.commandprocessor.ui.theme.codeFont
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.LinkedList

class MainActivity : ComponentActivity() {

    // Kotlin Flows that stream commands and output
    // commandsFlow: Commands from the UI -> Websocket connection
    // outputFlow: Output received from websocket -> UI
    private val commandsFlow : MutableSharedFlow<String> = MutableSharedFlow()
    private val outputFlow : MutableSharedFlow<String> = MutableSharedFlow()

    // Websocket parameters
    private val scheme = "http"
    private val host = "192.168.220.103"
    private val port = "8000"
    private val webSocketHandler = CommandProcessor( scheme , host , port , commandsFlow , outputFlow )

    // displayText holds the lines of the output in the form of
    // a LinkedList. The list is updated when displayTextLiveData is updated.
    private var currentWorkingDirectory = host
    private val displayText = LinkedList<String>()
    private val displayTextLiveData = MutableLiveData<LinkedList<String>>( LinkedList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CommandProcessorTheme {
                CommandPromptUI()
            }
        }
        webSocketHandler.connect()
        webSocketHandler.getWorkingDirectory{
            currentWorkingDirectory = it
        }

        // outputFlow will only stream the new output line that is received from the websocket
        // We need to append this output line to the existing output i.e. displayText
        // The recomposition of CommandPromptOutput is triggered by the change in the value
        // of displayTextLiveData
        CoroutineScope( Dispatchers.Main ).launch {
            outputFlow.collect {
                displayText.add( it )
                val newList = LinkedList( displayText )
                displayTextLiveData.value = newList
            }
        }
    }

    @Preview
    @Composable
    fun CommandPromptUI() {
        Column {
            // We build separate @Composable(s) for input and output displays.
            // The reason is that when the user is typing a command in the TextField ( in CommandPromptInput ),
            // a recomposition is triggered each time the text changes. Other widgets contained in
            // CommandOutput also react to these recompositions leading to repeated data fetching.
            CommandPromptOutput(
                modifier = Modifier
                    .background(Color.Black)
                    .weight(2.0f)
                    .fillMaxSize()
                    .padding( 8.dp )
            )
            CommandPromptInput()
        }
    }

    @Composable
    fun CommandPromptOutput(modifier: Modifier ) {
        val outputLines by displayTextLiveData.observeAsState()
        CommandOutputList( commandOutputLines = outputLines!! ,  modifier = modifier )
    }

    @Composable
    fun CommandOutputList( commandOutputLines : LinkedList<String> , modifier: Modifier ) {
        val listState = rememberLazyListState()
        LaunchedEffect( commandOutputLines.size ) {
            // This block will get executed when the `key` i.e. commandOutputLine.size will
            // change. It happens when a new item is added, and that's when we scroll
            // to the bottom of the list.
            if( commandOutputLines.size != 0 ) {
                listState.animateScrollToItem( commandOutputLines.size - 1 )
            }
        }
        LazyColumn( modifier = modifier , state = listState ) {
            items( commandOutputLines ) {
                    Text(text = it ,
                        modifier = Modifier.fillMaxWidth() ,
                        color = Color.White ,
                        fontSize = 8.sp ,
                        fontFamily = codeFont ,
                    )
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CommandPromptInput() {
        var commandText by rememberSaveable{ mutableStateOf( "" ) }
        Row( modifier = Modifier
            .height(60.dp)
            .fillMaxWidth() ) {
            TextField(
                value = commandText,
                onValueChange = { commandText = it } ,
                modifier = Modifier.fillMaxWidth().weight(1.0f).height(60.dp),
                placeholder = { Text(text = "Enter command here..." , fontFamily = codeFont ) } ,
                shape = RectangleShape ,
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color.White ,
                    textColor = Color.Black ,
                    disabledIndicatorColor = Color.Transparent ,
                    focusedIndicatorColor = Color.Transparent ,
                    unfocusedIndicatorColor = Color.Transparent
                ) ,
                textStyle = TextStyle( fontFamily = codeFont )
            )
            Button(
                onClick = {
                    CoroutineScope( Dispatchers.Default ).launch {
                        Log.e( "APP" , "Command emitted: $commandText")
                        commandsFlow.emit( commandText )
                        outputFlow.emit("\n $currentWorkingDirectory > $commandText")
                        commandText = ""
                    }
                } ,
                shape = RectangleShape ,
                colors = ButtonDefaults.buttonColors( containerColor = Color.White ) ,
                modifier = Modifier.height(70.dp).width(70.dp)
            ) {
                Icon( Icons.Default.PlayArrow, "Execute" , tint = Color.Black )
            }
        }
    }

}

