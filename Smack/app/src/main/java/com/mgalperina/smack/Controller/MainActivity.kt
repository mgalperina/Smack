package com.mgalperina.smack.Controller

import android.content.*
import android.graphics.Color
import android.os.Bundle
import android.service.autofill.UserData
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import com.mgalperina.smack.Adapters.MessageAdapter
import com.mgalperina.smack.Model.Channel
import com.mgalperina.smack.Model.Message
import com.mgalperina.smack.R
import com.mgalperina.smack.Services.AuthService
import com.mgalperina.smack.Services.MessageService
import com.mgalperina.smack.Services.UserDataService
import com.mgalperina.smack.Utilities.BROADCAST_USER_DATA_CHANGE
import com.mgalperina.smack.Utilities.SOCKET_URL
import io.socket.client.IO
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.nav_header_main.*

val STATUS_LOGGED_KEY = "logged_key"
val LOGGED_USERNAME_KEY = "logged_username_key"
val LOGGED_USEREMAIL_KEY = "logged_useremail_key"

class MainActivity : AppCompatActivity() {

    val socket = IO.socket(SOCKET_URL)
    lateinit var channelAdapter: ArrayAdapter<Channel>
    lateinit var messageAdapter: MessageAdapter
    var selectedChannel: Channel? = null

    private fun setupAdapters() {
        channelAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, MessageService.channels)
        channel_list.adapter = channelAdapter

        messageAdapter = MessageAdapter(this, MessageService.messages)
        messageListView.adapter = messageAdapter
        val layoutManager = LinearLayoutManager(this)
        messageListView.layoutManager = layoutManager
    }

    var userName = ""
    var userEmail = ""
    var isLoggedIn = false
    var userAvatarColor = Color.TRANSPARENT
    var userAvatarImage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loginButtonNavDrawer.setOnClickListener { v -> loginButtonNavClicked(v) }
        setSupportActionBar(toolbar)
        socket.connect()
        socket.on("channelCreated", onNewChannel)
        socket.on("messageCrested", onNewMessage)


        val toggle = ActionBarDrawerToggle(
            this, drawer_layout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        setupAdapters()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(userDataChangeReceiver, IntentFilter(BROADCAST_USER_DATA_CHANGE))

        channel_list.setOnItemClickListener { _, _, i, _ ->
            selectedChannel = MessageService.channels[i]
            drawer_layout.closeDrawer(GravityCompat.START)
            updateWithChannel()
        }

        if (App.prefs.isLoggedIn) {
            AuthService.findUserByEmail(this) { s, n, e -> }
        }

        setStatus(isLoggedIn, userName, userEmail)
    }

    override fun onDestroy() {
        socket.disconnect()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(userDataChangeReceiver)
        super.onDestroy()
    }

    private val userDataChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
//            if (AuthService.isLoggedIn) {
////                isLoggedIn = true
//////                navDrawerHeader.userNameNavHeader.text = UserDataService.name
//////                navDrawerHeader.userEmailNavHeader.text = UserDataService.email
//////                val resourceId = resources.getIdentifier(UserDataService.avatarName, "drawable", packageName)
//////                navDrawerHeader.userImageNavHeader.setImageResource(resourceId)
//////                navDrawerHeader.userImageNavHeader.setBackgroundColor(UserDataService.returnAvatarColor(UserDataService.avatarColor))
//////                navDrawerHeader.loginButtonNavDrawer.text = "Logout"
////
//////                    userNameNavHeader.text = UserDataService.name
//////                    userEmailNavHeader.text = UserDataService.email
//////                    val resourceId = resources.getIdentifier(UserDataService.avatarName, "drawable", packageName)
//////                    userImageNavHeader.setImageResource(resourceId)
//////                    userImageNavHeader.setBackgroundColor(UserDataService.returnAvatarColor(UserDataService.avatarColor))
//////                    loginButtonNavDrawer.text = "Logout"}

            if (App.prefs.isLoggedIn) {
                isLoggedIn = true
                userName = UserDataService.name
                userEmail = UserDataService.email
                userAvatarImage = resources.getIdentifier(UserDataService.avatarName, "drawable", packageName)
                userAvatarColor = UserDataService.returnAvatarColor(UserDataService.avatarColor)
            } else {
                isLoggedIn = false
                userName = ""
                userEmail = ""
                userAvatarImage = 0
                userAvatarColor = 0
            }
            setStatus(isLoggedIn, userName, userEmail)
            MessageService.getChannels { complete ->
                if (complete) {
                    if (MessageService.channels.count() > 0) {
                        selectedChannel = MessageService.channels[0]
                        channelAdapter.notifyDataSetChanged()
                        updateWithChannel()
                    }
                }
            }
        }
    }

    fun updateWithChannel() {
        mainChannelName.text = "#${selectedChannel?.name}"
        if (selectedChannel != null) {
            MessageService.getMessages(selectedChannel!!.id) {complete ->
                if (complete) {
                    messageAdapter.notifyDataSetChanged()
                    if (messageAdapter.itemCount > 0) {
                        messageListView.smoothScrollToPosition(messageAdapter.itemCount - 1)
                    }
                }
            }
        }
    }
    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    val TAG_LOGIN_ACTION_LAUCH = 1002

    fun loginButtonNavClicked(view: View) {
        isLoggedIn = App.prefs.isLoggedIn
        if (isLoggedIn) {
            UserDataService.logOut()
            channelAdapter.notifyDataSetChanged()
            messageAdapter.notifyDataSetChanged()
            isLoggedIn = false
            userName = ""
            userEmail = ""
            mainChannelName.text = "Please log in"
            setStatus()
        } else {
            val loginIntent = Intent(this, LoginActivity::class.java)
            startActivityForResult(loginIntent, TAG_LOGIN_ACTION_LAUCH)
        }
    }

    fun addChannelClicked(view: View) {
        if (App.prefs.isLoggedIn) {
            val builder = AlertDialog.Builder(this)
            val dialogView = layoutInflater.inflate(R.layout.add_channel_dialog, null)

            builder.setView(dialogView)
                .setPositiveButton("Add") { _, _ ->
                    val nameTextField = dialogView.findViewById<EditText>(R.id.addChannelNameTxt)
                    val descTextField = dialogView.findViewById<EditText>(R.id.addChannelDescTxt)
                    val channelName = nameTextField.text.toString()
                    val channelDesc = descTextField.text.toString()

                    socket.emit("newChannel", channelName, channelDesc)

                }
                .setNegativeButton("Cancel") { _, _ ->

                }
                .show()
        }
    }

    private val onNewChannel = Emitter.Listener { args ->
        if (App.prefs.isLoggedIn) {
            runOnUiThread {
                val channelName = args[0] as String
                val channelDescription = args[1] as String
                val channelId = args[2] as String

                val newChannel = Channel(channelName, channelDescription, channelId)
                MessageService.channels.add(newChannel)
                channelAdapter.notifyDataSetChanged()
            }

        }
    }

    private val onNewMessage = Emitter.Listener { args ->
        if(App.prefs.isLoggedIn) {
            runOnUiThread {
                val channelId = args[2] as String
                if (channelId == selectedChannel?.id) {

                    val msgBody = args[0] as String
                    val userName = args[3] as String
                    val userAvatar = args[4] as String
                    val userAvatarColor = args[5] as String
                    val id = args[6] as String
                    val timeStamp = args[7] as String

                    val newMessage = Message(msgBody, userName, channelId, userAvatar, userAvatarColor, id, timeStamp)
                    MessageService.messages.add(newMessage)
                    messageAdapter.notifyDataSetChanged()
                    messageListView.smoothScrollToPosition(messageAdapter.itemCount - 1)
                }
            }
        }

    }

    fun sendMsgBtnClicked(view: View) {
        if (App.prefs.isLoggedIn && messageTextField.text.isNotEmpty() && selectedChannel != null) {
            val userId = UserDataService.id
            val channelId = selectedChannel!!.id
            socket.emit(
                "newMessage",
                messageTextField.text.toString(),
                userId,
                channelId,
                UserDataService.name,
                UserDataService.avatarName,
                UserDataService.avatarColor
            )
            messageTextField.text.clear()
            hideKeyboard()
        }
    }

    private fun hideKeyboard() {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        if (inputManager.isAcceptingText) {
            inputManager.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != TAG_LOGIN_ACTION_LAUCH) {
            // login activity has send some result
            data?.let {
                if (it.getBooleanExtra(STATUS_LOGGED_KEY, false)) {
                    isLoggedIn = true
                    userName = it.getStringExtra(LOGGED_USERNAME_KEY)
                    userEmail = it.getStringExtra(LOGGED_USEREMAIL_KEY)
                } else {
                    // not logged in
                    isLoggedIn = false
                    userName = ""
                    userEmail = ""
                }
                setStatus(isLoggedIn, userName, userEmail)
            }
        }
    }

    private fun setStatus(loggedIn: Boolean = false, userName: String = "", userEmail: String = "") {
        when (loggedIn) {
            true -> {
                profileName.text = userName
                profileName.visibility = View.VISIBLE
                profileEmail.text = userEmail
                profileEmail.visibility = View.VISIBLE
                userImageNavHeader.setImageResource(userAvatarImage)
                userImageNavHeader.setBackgroundColor(UserDataService.returnAvatarColor(UserDataService.avatarColor))
                loginButtonNavDrawer.text = "LOGOUT"
            }
            else -> {
                profileName.text = ""
                profileName.visibility = View.INVISIBLE
                profileEmail.text = ""
                userImageNavHeader.setImageResource(R.drawable.profiledefault)
                userImageNavHeader.setBackgroundColor(Color.TRANSPARENT)
                profileEmail.visibility = View.VISIBLE
                loginButtonNavDrawer.text = "LOGIN"

            }
        }
    }

}

