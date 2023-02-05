package com.shubham0204.commandprocessor

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*

class CommandProcessor(
    scheme: String,
    host: String,
    port: String,
    private val commandsFlow : MutableSharedFlow<String>,
    private val outputFlow : MutableSharedFlow<String>
) {

    // scheme: http or https
    // host: IP address of the system where the websocket is hosted
    // port: The port over which the websocket is served
    private val websocketUrl = Url( "$scheme://$host:$port/run" )
    private val pwdUrl = Url( "$scheme://$host:$port/pwd" )

    private val ktorClient = HttpClient( OkHttp ){ install( WebSockets ) }
    private val ioScope = CoroutineScope( Dispatchers.IO )

    fun connect() {
        ioScope.launch {
            setupWebSocket()
        }
    }

    fun getWorkingDirectory( callback : (String) -> Unit ) {
        ioScope.launch{
            callback( getCurrentWorkingDir() )
        }
    }

    private suspend fun getCurrentWorkingDir() : String {
        return ktorClient.get( pwdUrl )
    }

    private suspend fun setupWebSocket() {
        ktorClient.ws( HttpMethod.Get ,
            websocketUrl.host ,
            websocketUrl.port ,
            websocketUrl.encodedPath
        ) {
            awaitAll(
                async { // Deferred Job 1
                    commandsFlow.collect{
                        Log.e( "APP" , "Command sent: $it")
                        outgoing.send( Frame.Text( it ) )
                    }
                } ,
                async { // Deferred Job 2
                    incoming.consumeEach {
                        Log.e( "APP" , "Output received: $it")
                        if( it is Frame.Text ) {
                            Log.e( "APP" , "Output emitted: ${it.readText()}")
                            outputFlow.emit( it.readText().removeSuffix( "\n" ) )
                        }
                    }
                }
            )
        }
    }

}