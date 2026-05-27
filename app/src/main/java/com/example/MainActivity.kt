package com.example

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.*
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import com.startapp.sdk.ads.banner.Banner
import com.unity3d.ads.UnityAds
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAdsShowOptions
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- SCREEN STATES ---
enum class ScreenState {
    ONBOARDING, // Acts as Login / Sing up
    HOME,
    REDEEM,
    HISTORY,
    ADMIN,
    MATCHMAKING,
    MATCH_FOUND,
    GAMEPLAY,
    GAMEOVER
}

// --- OPPONENTS POOL ---
data class OpponentProfile(
    val name: String,
    val avatarEmoji: String,
    val rankTitle: String,
    val winRate: String,
    val avatarBg: Brush,
    val tagline: String
)

val OPPONENTS_POOL = listOf(
    OpponentProfile(
        name = "LunaStar_✨",
        avatarEmoji = "💫",
        rankTitle = "Gold III",
        winRate = "51%",
        avatarBg = Brush.linearGradient(listOf(Color.Gray, Color.Gray)),
        tagline = "Let's make this a beautiful game! 🌸"
    ),
    OpponentProfile(
        name = "Alex_TicTac_🎮",
        avatarEmoji = "👾",
        rankTitle = "Gold I",
        winRate = "54%",
        avatarBg = Brush.linearGradient(listOf(Color.White, Color.LightGray)),
        tagline = "Down for a chill session. No tryhards! 🥤"
    ),
    OpponentProfile(
        name = "CrossMaster_X",
        avatarEmoji = "⚡",
        rankTitle = "Gold IV",
        winRate = "49%",
        avatarBg = Brush.linearGradient(listOf(Color.Gray, Color.Gray)),
        tagline = "Road to Platinum! Don't lag please. 😤"
    ),
    OpponentProfile(
        name = "BtrFingers_🍿",
        avatarEmoji = "🍕",
        rankTitle = "Silver II",
        winRate = "46%",
        avatarBg = Brush.linearGradient(listOf(Color.Gray, Color.Gray)),
        tagline = "typing from phone while eating pizza sry"
    ),
    OpponentProfile(
        name = "ZenPanda_🐼",
        avatarEmoji = "🎋",
        rankTitle = "Gold II",
        winRate = "50%",
        avatarBg = Brush.linearGradient(listOf(Color.Gray, Color.Gray)),
        tagline = "Focus like a mountain, flow like water. 🧘"
    )
)

// Opponent name obfuscation utility
fun maskOpponentName(name: String): String {
    return "UNKNOWN_PLAYER"
}

// --- VIEW MODEL ---
class TicTacToeViewModel(application: Application) : AndroidViewModel(application) {

    private fun getSharedPrefs(): SharedPreferences {
        return getApplication<Application>().getSharedPreferences("user_session", Context.MODE_PRIVATE)
    }

    private fun loadPointsLocally(): Int {
        return getSharedPrefs().getInt("points", 200)
    }

    private fun savePointsLocally(points: Int) {
        getSharedPrefs().edit().putInt("points", points).apply()
    }

    // Auth Session Info
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _userUid = MutableStateFlow<String?>(null)
    val userUid: StateFlow<String?> = _userUid.asStateFlow()

    private val _userPoints = MutableStateFlow(0)
    val userPoints: StateFlow<Int> = _userPoints.asStateFlow()

    private val _redeemHistory = MutableStateFlow<List<RedeemRequest>>(emptyList())
    val redeemHistory: StateFlow<List<RedeemRequest>> = _redeemHistory.asStateFlow()

    // Screen State
    private val _currentScreen = MutableStateFlow(ScreenState.ONBOARDING)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    // Loading & Error logs
    private val _isLoginLoading = MutableStateFlow(false)
    val isLoginLoading: StateFlow<Boolean> = _isLoginLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Matchmaking Variables
    private val _matchmakingTimeSec = MutableStateFlow(0)
    val matchmakingTimeSec: StateFlow<Int> = _matchmakingTimeSec.asStateFlow()

    // Game Variables
    private val _board = MutableStateFlow(List(9) { "" }) // "", "X", "O"
    val board: StateFlow<List<String>> = _board.asStateFlow()

    private val _isPlayerTurn = MutableStateFlow(true)
    val isPlayerTurn: StateFlow<Boolean> = _isPlayerTurn.asStateFlow()

    private val _opponent = MutableStateFlow(OPPONENTS_POOL.first())
    val opponent: StateFlow<OpponentProfile> = _opponent.asStateFlow()

    private val _isOpponentThinking = MutableStateFlow(false)
    val isOpponentThinking: StateFlow<Boolean> = _isOpponentThinking.asStateFlow()

    private val _winner = MutableStateFlow<String?>(null) // null, "X" (Player), "O" (Opponent), "DRAW"
    val winner: StateFlow<String?> = _winner.asStateFlow()

    private val _turnTimer = MutableStateFlow(15)
    val turnTimer: StateFlow<Int> = _turnTimer.asStateFlow()

    private val _pingMs = MutableStateFlow(32)
    val pingMs: StateFlow<Int> = _pingMs.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null

    // Admin Variables
    private val _allRedeemRequests = MutableStateFlow<List<RedeemRequest>>(emptyList())
    val allRedeemRequests: StateFlow<List<RedeemRequest>> = _allRedeemRequests.asStateFlow()

    data class LocalSessionHistory(val gameNumber: Int, val result: String, val movesCount: Int)

    private val _sessionHistory = MutableStateFlow<List<LocalSessionHistory>>(emptyList())
    val sessionHistory: StateFlow<List<LocalSessionHistory>> = _sessionHistory.asStateFlow()
    private var gameCounter = 1

    private val _allMatchHistory = MutableStateFlow<List<MatchRecordFirebase>>(emptyList())
    val allMatchHistory: StateFlow<List<MatchRecordFirebase>> = _allMatchHistory.asStateFlow()

    private val _userMatchHistory = MutableStateFlow<List<MatchRecordFirebase>>(emptyList())
    val userMatchHistory: StateFlow<List<MatchRecordFirebase>> = _userMatchHistory.asStateFlow()

    private val _isAdminLoading = MutableStateFlow(false)
    val isAdminLoading: StateFlow<Boolean> = _isAdminLoading.asStateFlow()

    fun setScreen(state: ScreenState) {
        _currentScreen.value = state
    }

    init {
        // Ping simulations
        viewModelScope.launch {
            while (true) {
                delay(1500)
                _pingMs.value = (24..62).random()
            }
        }
    }

    // Attempt auto login from SharedPreferences credentials
    fun checkAutoLogin(context: Context) {
        val sp = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val savedUid = sp.getString("uid", null)
        val savedEmail = sp.getString("email", null)

        if (savedUid != null && savedEmail != null) {
            _userUid.value = savedUid
            _userEmail.value = savedEmail
            _userPoints.value = loadPointsLocally()
            _currentScreen.value = ScreenState.HOME
            refreshUserProfile()
            loadRedeemHistory()
            loadUserMatchHistory()
            if (savedEmail == "vfttffx57@gmail.com") {
                loadAdminData()
            }
        }
    }

    fun login(email: String, password: String, context: Context) {
        if (email.isEmpty() || password.isEmpty()) {
            _authError.value = "Email and password cannot be empty."
            return
        }
        viewModelScope.launch {
            _isLoginLoading.value = true
            _authError.value = null
            val result = FirebaseService.signIn(email, password)
            if (result.success && result.uid != null && result.email != null) {
                _userUid.value = result.uid
                _userEmail.value = result.email
                
                // Save session persistence
                val sp = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                sp.edit().putString("uid", result.uid).putString("email", result.email).apply()

                // Load Firestore profile
                val profile = FirebaseService.getUserProfile(result.uid)
                if (profile != null) {
                    _userPoints.value = profile.points
                    savePointsLocally(profile.points)
                } else {
                    // Initialize first if profile failed to fetch
                    FirebaseService.saveUserProfile(result.uid, result.email, 200)
                    _userPoints.value = 200
                    savePointsLocally(200)
                }

                _currentScreen.value = ScreenState.HOME
                loadRedeemHistory()
                loadUserMatchHistory()
                if (result.email == "vfttffx57@gmail.com") {
                    loadAdminData()
                }
            } else {
                _authError.value = result.errorMessage ?: "Authentication failed."
            }
            _isLoginLoading.value = false
        }
    }

    fun signUp(email: String, password: String, context: Context) {
        if (email.isEmpty() || password.isEmpty()) {
            _authError.value = "Email and password cannot be empty."
            return
        }
        if (password.length < 6) {
            _authError.value = "Password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _isLoginLoading.value = true
            _authError.value = null
            val result = FirebaseService.signUp(email, password)
            if (result.success && result.uid != null && result.email != null) {
                _userUid.value = result.uid
                _userEmail.value = result.email
                
                // Save session persistence
                val sp = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                sp.edit().putString("uid", result.uid).putString("email", result.email).apply()

                // Check first if profile already exists in Firestore (for user recovery/re-registration)
                val existingProfile = FirebaseService.getUserProfile(result.uid)
                if (existingProfile != null) {
                    _userPoints.value = existingProfile.points
                    savePointsLocally(existingProfile.points)
                } else {
                    // Register 200 Points default welcome balance in Cloud Database
                    FirebaseService.saveUserProfile(result.uid, result.email, 200)
                    _userPoints.value = 200
                    savePointsLocally(200)
                }

                _currentScreen.value = ScreenState.HOME
                loadRedeemHistory()
                loadUserMatchHistory()
            } else {
                _authError.value = result.errorMessage ?: "Signup failed. Try different credentials."
            }
            _isLoginLoading.value = false
        }
    }

    fun logout(context: Context) {
        val sp = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        sp.edit().clear().apply()
        _userUid.value = null
        _userEmail.value = null
        _userPoints.value = 0
        _redeemHistory.value = emptyList()
        _currentScreen.value = ScreenState.ONBOARDING
    }

    fun refreshUserProfile() {
        val uid = _userUid.value ?: return
        viewModelScope.launch {
            val profile = FirebaseService.getUserProfile(uid)
            if (profile != null) {
                _userPoints.value = profile.points
                savePointsLocally(profile.points)
            }
        }
    }

    fun loadRedeemHistory() {
        val uid = _userUid.value ?: return
        viewModelScope.launch {
            val list = FirebaseService.getRedeemRequests(uid)
            _redeemHistory.value = list
        }
    }

    fun loadUserMatchHistory() {
        val uid = _userUid.value ?: return
        viewModelScope.launch {
            val allMatches = FirebaseService.getMatchHistoryAll()
            _userMatchHistory.value = allMatches.filter { it.uid == uid }
        }
    }

    fun watchRewardedAdForPoints(activity: android.app.Activity) {
        val uid = _userUid.value ?: return
        val email = _userEmail.value ?: return
        
        _isLoginLoading.value = true
        AdManager.showRewarded(
            activity = activity,
            onRewardEarned = { amount ->
                viewModelScope.launch {
                    val newBalance = _userPoints.value + 150
                    FirebaseService.saveUserProfile(uid, email, newBalance)
                    _userPoints.value = newBalance
                    savePointsLocally(newBalance)
                    Toast.makeText(activity, "Successfully earned +150 Points!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                _isLoginLoading.value = false
                refreshUserProfile()
            }
        )
    }

    fun claimRedeemRequest(context: Context) {
        val uid = _userUid.value ?: return
        val email = _userEmail.value ?: return
        val current = _userPoints.value

        if (current < 1000) {
            Toast.makeText(context, "Insufficient Points. Need at least 1000 Points to claim code.", Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch {
            val netPoints = current - 1000
            val savedOk = FirebaseService.saveUserProfile(uid, email, netPoints)
            if (savedOk) {
                _userPoints.value = netPoints
                savePointsLocally(netPoints)
                val submitted = FirebaseService.submitRedeemRequest(uid, email, 1000)
                if (submitted) {
                    Toast.makeText(context, "Successfully Claimed ₹10 Play Store Card! Pending Admin confirmation.", Toast.LENGTH_LONG).show()
                    loadRedeemHistory()
                } else {
                    Toast.makeText(context, "Request logged, network connection synced.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Cloud sync pending. Try again later.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- GAME QUEUE & MATCH PLAY FLOW ---
    fun startPlayMatchFlow(activity: android.app.Activity) {
        startMatchmaking()
    }

    fun startMatchmaking() {
        _currentScreen.value = ScreenState.MATCHMAKING
        _matchmakingTimeSec.value = 0
        viewModelScope.launch {
            for (i in 1..4) {
                delay(1000)
                _matchmakingTimeSec.value = i
            }
            // Pick a random styled opponent
            _opponent.value = OPPONENTS_POOL.random()
            _currentScreen.value = ScreenState.MATCH_FOUND
        }
    }

    fun setupNewGame() {
        _board.value = List(9) { "" }
        _winner.value = null
        _currentScreen.value = ScreenState.GAMEPLAY

        // Decides who starts match (50/50 Chance)
        val playerStarts = (0..1).random() == 1
        _isPlayerTurn.value = playerStarts

        startTurnTimer()

        if (!playerStarts) {
            triggerOpponentMoveDelay()
        }
    }

    private fun startTurnTimer() {
        timerJob?.cancel()
        _turnTimer.value = 15
        timerJob = viewModelScope.launch {
            while (_winner.value == null) {
                delay(1000)
                if (_turnTimer.value > 0) {
                    _turnTimer.value -= 1
                } else {
                    // Turn times out -> Auto plays random move for active side
                    if (_isPlayerTurn.value) {
                        val available = _board.value.indices.filter { _board.value[it].isEmpty() }
                        if (available.isNotEmpty()) {
                            makePlayerMove(available.random())
                        }
                    }
                }
            }
        }
    }

    fun makePlayerMove(index: Int) {
        if (!_isPlayerTurn.value || _board.value[index].isNotEmpty() || _winner.value != null || _isOpponentThinking.value) return

        val newBoard = _board.value.toMutableList()
        newBoard[index] = "X" // User is always X
        _board.value = newBoard

        // Check winner status
        val currentWinner = checkWinnerState(newBoard)
        if (currentWinner != null) {
            handleGameOver(currentWinner)
        } else {
            _isPlayerTurn.value = false
            startTurnTimer()
            triggerOpponentMoveDelay()
        }
    }

    private fun triggerOpponentMoveDelay() {
        _isOpponentThinking.value = true
        viewModelScope.launch {
            // Simulated computer analysis speed
            val analysisDelay = (1100..2000).random().toLong()
            delay(analysisDelay)

            if (_winner.value != null) return@launch

            makeOpponentMove()
            _isOpponentThinking.value = false

            val currentWinner = checkWinnerState(_board.value)
            if (currentWinner != null) {
                handleGameOver(currentWinner)
            } else {
                _isPlayerTurn.value = true
                startTurnTimer()
            }
        }
    }

    private fun makeOpponentMove() {
        val currentBoard = _board.value
        val playerSym = "X"
        val compSym = "O"

        // Unbeatable perfectly strategic AI
        val selectedMove = selectComputerMove(currentBoard, playerSym, compSym)

        if (selectedMove != -1) {
            val newBoard = currentBoard.toMutableList()
            newBoard[selectedMove] = compSym
            _board.value = newBoard
        }
    }

    // Minimax perfect play selector (with 30% fallback chance)
    private fun selectComputerMove(board: List<String>, playerSym: String, compSym: String): Int {
        val availableIdx = board.indices.filter { board[it].isEmpty() }
        if (availableIdx.isEmpty()) return -1

        if (availableIdx.size == 9) {
            return listOf(4, 0, 2, 6, 8).random()
        }

        if ((1..100).random() <= 30) {
            return availableIdx.random()
        }

        var bestVal = -1000
        var bestMove = -1

        for (idx in availableIdx) {
            val nextBoard = board.toMutableList()
            nextBoard[idx] = compSym
            val moveVal = minimax(nextBoard, 0, false, compSym, playerSym)
            if (moveVal > bestVal) {
                bestMove = idx
                bestVal = moveVal
            }
        }
        return bestMove
    }

    private fun minimax(board: List<String>, depth: Int, isMaximizing: Boolean, compSym: String, playerSym: String): Int {
        val score = evaluateBoard(board, compSym, playerSym)

        if (score == 10) return score - depth
        if (score == -10) return score + depth
        if (!board.any { it.isEmpty() }) return 0

        return if (isMaximizing) {
            var best = -1000
            for (i in board.indices) {
                if (board[i].isEmpty()) {
                    val nextBoard = board.toMutableList()
                    nextBoard[i] = compSym
                    val valMinimax = minimax(nextBoard, depth + 1, false, compSym, playerSym)
                    best = maxOf(best, valMinimax)
                }
            }
            best
        } else {
            var best = 1000
            for (i in board.indices) {
                if (board[i].isEmpty()) {
                    val nextBoard = board.toMutableList()
                    nextBoard[i] = playerSym
                    val valMinimax = minimax(nextBoard, depth + 1, true, compSym, playerSym)
                    best = minOf(best, valMinimax)
                }
            }
            best
        }
    }

    private fun evaluateBoard(board: List<String>, compSym: String, playerSym: String): Int {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            val a = board[line[0]]
            val b = board[line[1]]
            val c = board[line[2]]
            if (a.isNotEmpty()) {
                if (a == compSym && b == compSym && c == compSym) return 10
                if (a == playerSym && b == playerSym && c == playerSym) return -10
            }
        }
        return 0
    }

    private fun checkWinnerState(board: List<String>): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            val a = board[line[0]]
            val b = board[line[1]]
            val c = board[line[2]]
            if (a.isNotEmpty() && a == b && a == c) {
                return a // X or O
            }
        }
        if (board.none { it.isEmpty() }) return "DRAW"
        return null
    }

    private fun handleGameOver(winnerSymbol: String) {
        _winner.value = winnerSymbol
        timerJob?.cancel()

        val uid = _userUid.value ?: return
        val email = _userEmail.value ?: return
        val isWin = winnerSymbol == "X"
        val isDraw = winnerSymbol == "DRAW"

        // jeetne per 300 points milga aur haarne per 50 points gathegs
        val pointsChange = if (isWin) 300 else if (isDraw) 0 else -50
        val finalPoints = (_userPoints.value + pointsChange).coerceAtLeast(0)

        viewModelScope.launch {
            // Update balance in Cloud Firebase database
            FirebaseService.saveUserProfile(uid, email, finalPoints)
            _userPoints.value = finalPoints
            savePointsLocally(finalPoints)

            // Record outcome to match logs
            val outcome = if (isWin) "WIN" else if (isDraw) "DRAW" else "LOSS"
            val localResult = if (isWin) "Player Won" else if (isDraw) "Draw" else "AI Won"
            val movesCount = _board.value.count { it.isNotEmpty() }
            val sessionList = _sessionHistory.value.toMutableList()
            sessionList.add(LocalSessionHistory(gameCounter++, localResult, movesCount))
            _sessionHistory.value = sessionList

            FirebaseService.recordMatch(uid, email, outcome, pointsChange)

            delay(1200)
            _currentScreen.value = ScreenState.GAMEOVER
        }
    }

    fun adminAddPoints(pointsToAdd: Int, context: Context) {
        val uid = _userUid.value ?: return
        val email = _userEmail.value ?: return
        viewModelScope.launch {
            val newBalance = (_userPoints.value + pointsToAdd).coerceAtLeast(0)
            val ok = FirebaseService.saveUserProfile(uid, email, newBalance)
            if (ok) {
                _userPoints.value = newBalance
                savePointsLocally(newBalance)
                Toast.makeText(context, "Admin: Added $pointsToAdd points! New balance: $newBalance", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Sync error updating admin points", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playAgain(activity: android.app.Activity) {
        startPlayMatchFlow(activity)
    }

    fun exitToHome(activity: android.app.Activity?) {
        if (activity != null) {
            AdManager.showInterstitial(activity) {
                proceedExitToHome()
            }
        } else {
            proceedExitToHome()
        }
    }

    private fun proceedExitToHome() {
        refreshUserProfile()
        loadRedeemHistory()
        loadUserMatchHistory()
        _currentScreen.value = ScreenState.HOME
    }

    // --- ADMIN DASHBOARD PANEL ---
    fun loadAdminData() {
        viewModelScope.launch {
            _isAdminLoading.value = true
            val reqs = FirebaseService.getRedeemRequests(null)
            _allRedeemRequests.value = reqs

            val matches = FirebaseService.getMatchHistoryAll()
            _allMatchHistory.value = matches
            _isAdminLoading.value = false
        }
    }

    fun approveWithdrawRequest(requestId: String, redeemCode: String, context: Context) {
        if (redeemCode.trim().isEmpty()) {
            Toast.makeText(context, "Enter a valid Google Play Card Code.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            val ok = FirebaseService.updateRedeemRequest(requestId, redeemCode.trim())
            if (ok) {
                Toast.makeText(context, "Redeem Code sent successfully to user!", Toast.LENGTH_SHORT).show()
                loadAdminData()
            } else {
                Toast.makeText(context, "Failed to update codepass. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class TicTacToeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TicTacToeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TicTacToeViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- ACTIVITY MAIN ENTRY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdManager.init(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val app = context.applicationContext as Application
                val viewModel: TicTacToeViewModel = viewModel(factory = TicTacToeViewModelFactory(app))

                // Checking if user is pre-authenticated
                LaunchedEffect(Unit) {
                    viewModel.checkAutoLogin(context)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black // Cosmic slate dark base color
                ) {
                    AppScreenContainer(viewModel)
                }
            }
        }
    }
}

// --- INMOBI AD MANAGER ---
object AdManager {
    private const val UNITY_GAME_ID = "6010039"
    private const val UNITY_REWARDED_AD_UNIT = "Rewarded_Android"
    private const val UNITY_INTERSTITIAL_AD_UNIT = "Interstitial_Android"
    
    var isUnityInitialized = false
    var unityRewardedLoaded = false
    var unityInterstitialLoaded = false
    
    var rewardedAd: StartAppAd? = null
    var interstitialAd: StartAppAd? = null
    
    var pageChangeCounter = 0

    fun init(context: android.content.Context) {
        // Start.io init
        StartAppSDK.setTestAdsEnabled(false)
        StartAppSDK.init(context, "204327585", false)
        StartAppAd.disableSplash()
        loadRewardedStartApp(context)
        loadInterstitialStartApp(context)
        
        // Unity init
        UnityAds.initialize(
            context,
            UNITY_GAME_ID,
            false, // test mode
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    isUnityInitialized = true
                    Log.d("AdManager", "Unity Ads Initialized")
                    loadUnityRewarded()
                    loadUnityInterstitial()
                }

                override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                    Log.e("AdManager", "Unity Ads Init Failed: $message")
                }
            }
        )
    }

    fun loadRewardedStartApp(context: android.content.Context) {
        rewardedAd = StartAppAd(context)
        rewardedAd?.loadAd(StartAppAd.AdMode.REWARDED_VIDEO)
    }

    fun loadUnityRewarded() {
        if (!isUnityInitialized) return
        UnityAds.load(UNITY_REWARDED_AD_UNIT, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                unityRewardedLoaded = true
            }
            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                unityRewardedLoaded = false
            }
        })
    }
    
    fun loadInterstitialStartApp(context: android.content.Context) {
        interstitialAd = StartAppAd(context)
        interstitialAd?.loadAd(StartAppAd.AdMode.AUTOMATIC)
    }

    fun loadUnityInterstitial() {
        if (!isUnityInitialized) return
        UnityAds.load(UNITY_INTERSTITIAL_AD_UNIT, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                unityInterstitialLoaded = true
            }
            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                unityInterstitialLoaded = false
            }
        })
    }

    fun showRewarded(activity: android.app.Activity, onRewardEarned: (Int) -> Unit, onDismiss: () -> Unit) {
        if (unityRewardedLoaded) {
            UnityAds.show(activity, UNITY_REWARDED_AD_UNIT, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                    unityRewardedLoaded = false
                    // Fallback to Start.io
                    showStartAppRewarded(activity, onRewardEarned, onDismiss)
                    loadUnityRewarded()
                }

                override fun onUnityAdsShowStart(placementId: String) {}
                override fun onUnityAdsShowClick(placementId: String) {}

                override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                    unityRewardedLoaded = false
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        onRewardEarned(150)
                    }
                    onDismiss()
                    loadUnityRewarded()
                }
            })
        } else {
            showStartAppRewarded(activity, onRewardEarned, onDismiss)
            loadUnityRewarded()
        }
    }

    private fun showStartAppRewarded(activity: android.app.Activity, onRewardEarned: (Int) -> Unit, onDismiss: () -> Unit) {
        if (rewardedAd != null && rewardedAd!!.isReady) {
            rewardedAd?.setVideoListener(object: VideoListener {
                override fun onVideoCompleted() {
                    onRewardEarned(150)
                }
            })
            rewardedAd?.showAd(object : AdDisplayListener {
                override fun adHidden(ad: com.startapp.sdk.adsbase.Ad?) {
                    onDismiss()
                    loadRewardedStartApp(activity)
                }
                override fun adDisplayed(ad: com.startapp.sdk.adsbase.Ad?) {}
                override fun adClicked(ad: com.startapp.sdk.adsbase.Ad?) {}
                override fun adNotDisplayed(ad: com.startapp.sdk.adsbase.Ad?) {
                    onDismiss()
                    loadRewardedStartApp(activity)
                }
            })
        } else {
            showSimulatedSponsorAd(activity, onRewardEarned, onDismiss)
            loadRewardedStartApp(activity)
        }
    }

    fun handlePageChange(activity: android.app.Activity) {
        pageChangeCounter++
        if (pageChangeCounter >= 5) {
            pageChangeCounter = 0
            showInterstitial(activity)
        }
    }

    fun showInterstitial(activity: android.app.Activity, onDismiss: () -> Unit = {}) {
        if (unityInterstitialLoaded) {
            UnityAds.show(activity, UNITY_INTERSTITIAL_AD_UNIT, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                    unityInterstitialLoaded = false
                    // Fallback to Start.io
                    showStartAppInterstitial(activity, onDismiss)
                    loadUnityInterstitial()
                }

                override fun onUnityAdsShowStart(placementId: String) {}
                override fun onUnityAdsShowClick(placementId: String) {}

                override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                    unityInterstitialLoaded = false
                    onDismiss()
                    loadUnityInterstitial()
                }
            })
        } else {
            showStartAppInterstitial(activity, onDismiss)
            loadUnityInterstitial()
        }
    }

    private fun showStartAppInterstitial(activity: android.app.Activity, onDismiss: () -> Unit = {}) {
        if (interstitialAd != null && interstitialAd!!.isReady) {
            interstitialAd?.showAd(object : AdDisplayListener {
                override fun adHidden(ad: com.startapp.sdk.adsbase.Ad?) {
                    onDismiss()
                    loadInterstitialStartApp(activity)
                }
                override fun adDisplayed(ad: com.startapp.sdk.adsbase.Ad?) {}
                override fun adClicked(ad: com.startapp.sdk.adsbase.Ad?) {}
                override fun adNotDisplayed(ad: com.startapp.sdk.adsbase.Ad?) {
                    onDismiss()
                    loadInterstitialStartApp(activity)
                }
            })
        } else {
            onDismiss()
            loadInterstitialStartApp(activity)
        }
    }
}

fun showLoadingBlockingDialog(
    activity: android.app.Activity,
    message: String,
    onCancel: () -> Unit
): android.app.AlertDialog {
    val builder = android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
    val progressBar = android.widget.ProgressBar(activity)
    progressBar.setPadding(20, 20, 20, 20)
    builder.setView(progressBar)
    builder.setTitle(message)
    builder.setNegativeButton("Cancel") { dialog, _ ->
        onCancel()
        dialog.dismiss()
    }
    val dial = builder.create()
    dial.setCancelable(false)
    dial.setCanceledOnTouchOutside(false)
    dial.show()
    return dial
}

fun showSimulatedSponsorAd(
    activity: android.app.Activity,
    onRewardEarned: (Int) -> Unit,
    onDismiss: () -> Unit
): android.app.Dialog {
    val dialog = android.app.Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
        try {
            val lifecycleClazz = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val setLifecycleMethod = lifecycleClazz.getMethod(
                "set",
                android.view.View::class.java,
                androidx.lifecycle.LifecycleOwner::class.java
            )
            setLifecycleMethod.invoke(null, this, activity)
        } catch (e: Exception) {
            Log.e("AdManager", "VTreeLifecycle error: ${e.message}")
        }

        try {
            val viewModelClazz = Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
            val setViewModelMethod = viewModelClazz.getMethod(
                "set",
                android.view.View::class.java,
                androidx.lifecycle.ViewModelStoreOwner::class.java
            )
            setViewModelMethod.invoke(null, this, activity)
        } catch (e: Exception) {
            Log.e("AdManager", "VTreeViewModelStoreOwner error: ${e.message}")
        }

        try {
            val savedStateClazz = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            val setSavedStateMethod = savedStateClazz.getMethod(
                "set",
                android.view.View::class.java,
                androidx.savedstate.SavedStateRegistryOwner::class.java
            )
            setSavedStateMethod.invoke(null, this, activity)
        } catch (e: Exception) {
            Log.e("AdManager", "VTreeSavedStateRegistry error: ${e.message}")
        }

        setContent {
            var timeLeft by remember { mutableStateOf(5) }
            var isCompleted by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                while (timeLeft > 0) {
                    delay(1000L)
                    timeLeft--
                }
                isCompleted = true
                onRewardEarned(1)
                delay(1200L)
                dialog.dismiss()
                onDismiss()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isCompleted) Color.White else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GOOGLE PLAY SPONSOR NETWORK",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isCompleted) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isCompleted) "✓ REWARDED" else "Reward in ${timeLeft}s",
                                color = if (isCompleted) Color.White else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 32.dp)
                            .border(1.dp, Color.DarkGray, RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Sponsor Google Play Code Payout",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Supported entirely by user sponsor views. Support play store gift redemption! Complete 5s ad, click with absolute security.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { if (isCompleted) 1.0f else (5f - timeLeft) / 5f },
                            color = Color.White,
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isCompleted) "✓ YOUR REWARD SECURED! Closing in 1s..." else "Validating backup ad impression... Do not close.",
                            fontSize = 11.sp,
                            color = if (isCompleted) Color.White else Color.Gray,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
    dialog.setCancelable(false)
    dialog.setCanceledOnTouchOutside(false)
    dialog.setContentView(composeView)
    dialog.show()
    return dialog
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun AdmobBanner(modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            com.startapp.sdk.ads.banner.Banner(context)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(55.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(Color.Black, RoundedCornerShape(10.dp))
            .border(1.dp, Color.DarkGray, RoundedCornerShape(10.dp))
    )
}

// --- COMPOSE CORE SCREEN ROUTER ---
@Composable
fun AppScreenContainer(viewModel: TicTacToeViewModel) {
    val screenState by viewModel.currentScreen.collectAsState()
    val email by viewModel.userEmail.collectAsState()
    val context = LocalContext.current

    // Track page changes for Interstitial Ads
    LaunchedEffect(screenState) {
        val act = context.findActivity()
        if (act != null) {
            AdManager.handlePageChange(act)
        }
    }

    // Determine if we should show the fixed navigation bar
    val showNavBar = screenState == ScreenState.HOME || 
                     screenState == ScreenState.REDEEM || 
                     screenState == ScreenState.HISTORY || 
                     screenState == ScreenState.ADMIN

    Scaffold(
        bottomBar = {
            if (showNavBar) {
                NavigationBar(
                    containerColor = Color.Black,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(1.dp, Color.DarkGray)
                ) {
                    // 1. Play Arena Tab
                    NavigationBarItem(
                        selected = screenState == ScreenState.HOME,
                        onClick = { viewModel.setScreen(ScreenState.HOME) },
                        icon = { 
                            Icon(
                                imageVector = Icons.Default.PlayArrow, 
                                contentDescription = "Play Arena", 
                                tint = if (screenState == ScreenState.HOME) Color.White else Color.Gray
                            ) 
                        },
                        label = { 
                            Text(
                                "Play", 
                                color = if (screenState == ScreenState.HOME) Color.White else Color.Gray, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 11.sp
                            ) 
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.DarkGray
                        )
                    )

                    // 2. Redeem Rewards Tab
                    NavigationBarItem(
                        selected = screenState == ScreenState.REDEEM,
                        onClick = { 
                            viewModel.loadRedeemHistory()
                            viewModel.refreshUserProfile()
                            viewModel.setScreen(ScreenState.REDEEM) 
                        },
                        icon = { 
                            Icon(
                                imageVector = Icons.Default.ShoppingCart, 
                                contentDescription = "Redeem Rewards", 
                                tint = if (screenState == ScreenState.REDEEM) Color.White else Color.Gray
                            ) 
                        },
                        label = { 
                            Text(
                                "Redeem", 
                                color = if (screenState == ScreenState.REDEEM) Color.White else Color.Gray, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 11.sp
                            ) 
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.DarkGray
                        )
                    )

                    // 3. Activity History Tab
                    NavigationBarItem(
                        selected = screenState == ScreenState.HISTORY,
                        onClick = { 
                            viewModel.loadUserMatchHistory()
                            viewModel.loadRedeemHistory()
                            viewModel.setScreen(ScreenState.HISTORY) 
                        },
                        icon = { 
                            Icon(
                                imageVector = Icons.Default.List, 
                                contentDescription = "History Logs", 
                                tint = if (screenState == ScreenState.HISTORY) Color.White else Color.Gray
                            ) 
                        },
                        label = { 
                            Text(
                                "History", 
                                color = if (screenState == ScreenState.HISTORY) Color.White else Color.Gray, 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 11.sp
                            ) 
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.DarkGray
                        )
                    )

                    // 4. Admin Settings Tab (Conditional based on admin email ID)
                    if (email == "vfttffx57@gmail.com") {
                        NavigationBarItem(
                            selected = screenState == ScreenState.ADMIN,
                            onClick = { 
                                viewModel.loadAdminData()
                                viewModel.setScreen(ScreenState.ADMIN) 
                            },
                            icon = { 
                                Icon(
                                    imageVector = Icons.Default.Settings, 
                                    contentDescription = "Admin", 
                                    tint = if (screenState == ScreenState.ADMIN) Color.White else Color.Gray
                                ) 
                            },
                            label = { 
                                Text(
                                    "Admin", 
                                    color = if (screenState == ScreenState.ADMIN) Color.White else Color.Gray, 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 11.sp
                                ) 
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.DarkGray
                            )
                        )
                    }
                }
            }
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = screenState,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                },
                label = "RouterTransitions"
            ) { target ->
                when (target) {
                    ScreenState.ONBOARDING -> FirebaseLoginSetupScreen(viewModel)
                    ScreenState.HOME -> HomeDashboardScreen(viewModel)
                    ScreenState.REDEEM -> RedeemScreen(viewModel)
                    ScreenState.HISTORY -> UserHistoryScreen(viewModel)
                    ScreenState.ADMIN -> AdminPanelScreen(viewModel)
                    ScreenState.MATCHMAKING -> MatchmakingScreen(viewModel)
                    ScreenState.MATCH_FOUND -> MatchFoundCards(viewModel)
                    ScreenState.GAMEPLAY -> GameplayScreen(viewModel)
                    ScreenState.GAMEOVER -> GameOverScreen(viewModel)
                }
            }
        }
    }
}

// --- SCREEN 1: FIREBASE LOGIN / REGISTRATION ---
@Composable
fun FirebaseLoginSetupScreen(viewModel: TicTacToeViewModel) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    val isLoading by viewModel.isLoginLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val context = LocalContext.current

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Black, Color.Black)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity Brand Icon
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)), CircleShape)
                .wrapContentSize(Alignment.Center)
        ) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .size(70.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .shadow(12.dp, CircleShape)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "⚔️", fontSize = 34.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "TITAN ARENA",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Text(
            text = "PLAY TIC TAC TOE • EARN PLAY CARDS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.DarkGray, RoundedCornerShape(24.dp))
                .shadow(16.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignUpMode) "CREATE CHAMPION PROFILE" else "CHAMPION SECURE LOGIN",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Text(
                    text = if (isSignUpMode) "Sign up to start earning redeem codes!" else "Sign in to access your dashboard and points",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Email Input
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email Address", color = Color.Gray) },
                    placeholder = { Text("username@domain.com", color = Color.Gray) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "EmailIcon",
                            tint = Color.White
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password Input with Show/Hide visibility
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password", color = Color.Gray) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "LockIcon",
                            tint = Color.White
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = Color.Gray
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                if (authError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = authError ?: "",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Box(modifier = Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            if (isSignUpMode) {
                                viewModel.signUp(emailInput, passwordInput, context)
                            } else {
                                viewModel.login(emailInput, passwordInput, context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(8.dp, RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = if (isSignUpMode) "GET WELCOME BONUS (+200)" else "CHAMPION SIGN IN",
                            color = Color.Black,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                fontSize = 14.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isSignUpMode) "Already have an account? Sign In" else "Don't have an account? Create One",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { isSignUpMode = !isSignUpMode }
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

// --- SCREEN 2: HOME DASHBOARD (SIMPLIFIED) ---
@Composable
fun HomeDashboardScreen(viewModel: TicTacToeViewModel) {
    val points by viewModel.userPoints.collectAsState()
    val email by viewModel.userEmail.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        val sp = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
        if (email == "vfttffx57@gmail.com" && !sp.getBoolean("added_1000_pts", false)) {
            viewModel.adminAddPoints(1000, context)
            sp.edit().putBoolean("added_1000_pts", true).apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top HUD Bar showing email and current Points
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = email ?: "Player Session",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "STATUS: ONLINE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Beautiful Top Bar Points Display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.DarkGray)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(30.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(text = "⭐", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$points PTS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Play versus AI option card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            // Welcome Card showing value conversion
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🎁", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "1000 Points = ₹10 Redeem Code",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Win: +300 PTS | Loss: -50 PTS. Win matches and claim Google Play recharge instantly!",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Large Play Button Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable {
                        if (activity != null) {
                            viewModel.startPlayMatchFlow(activity)
                        }
                    }
                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🛡️", fontSize = 32.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "PLAY CHALLENGE MATCH",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Requires watching a Google Play sponsor ad. Safe, instant connection.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (activity != null) {
                                viewModel.startPlayMatchFlow(activity)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "FIND OPPONENT", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val sessionHistory by viewModel.sessionHistory.collectAsState()
            if (sessionHistory.isNotEmpty()) {
                Text(
                    text = "CURRENT SESSION HISTORY",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.DarkGray, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    items(sessionHistory.reversed()) { match ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Game #${match.gameNumber}", color = Color.LightGray, fontSize = 12.sp)
                            Text(match.result, color = if (match.result == "Player Won") Color.Green else if (match.result == "Draw") Color.Yellow else Color.Red, fontSize = 12.sp)
                            Text("Moves: ${match.movesCount}", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SIGN OUT SESSION",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { viewModel.logout(context) }
                        .padding(12.dp)
                )
            }
        }
    }
}

// --- SCREEN 3: REDEEM POINTS AND REDEEM HISTORY ---
@Composable
fun RedeemScreen(viewModel: TicTacToeViewModel) {
    val points by viewModel.userPoints.collectAsState()
    val history by viewModel.redeemHistory.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REDEEM REWARDS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Current points display panel
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "CURRENT REDEEMABLE BALANCE", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "⭐ $points PTS", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text(text = "Value: ₹${(points / 100.0).format(2)} INR", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "AVAILABLE VOUCHERS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // ₹10 Redeem option Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🎮", fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("₹10 Play Store Card", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Costs: 1000 Points", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.claimRedeemRequest(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("CLAIM", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "REDEEM HISTORY LOGS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            if (history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color.Black, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No redemptions submitted yet.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                items(history) { req ->
                    RedeemHistoryRow(req)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// Float output formatter safely
fun Double.format(digits: Int) = java.lang.String.format("%.${digits}f", this)

// --- SCREEN 3B: USER HISTORY SCREEN (COMBINES MATCHES LOGS AND REDEEM CODES) ---
@Composable
fun UserHistoryScreen(viewModel: TicTacToeViewModel) {
    val history by viewModel.redeemHistory.collectAsState()
    val matches by viewModel.userMatchHistory.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: Match Logs, 1: Gift Cards
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isRefreshing = true
        viewModel.loadUserMatchHistory()
        viewModel.loadRedeemHistory()
        delay(600)
        isRefreshing = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MY ACTIVITY HISTORY",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            IconButton(
                onClick = {
                    scope.launch {
                        isRefreshing = true
                        viewModel.loadUserMatchHistory()
                        viewModel.loadRedeemHistory()
                        delay(600)
                        isRefreshing = false
                    }
                }
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh history",
                        tint = Color.White
                    )
                }
            }
        }

        SpanTabs(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            tabs = listOf("Game matches arena", "Claimed gift codes")
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            // Match History Tab
            if (matches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("🎮", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No Arena matches played yet.", 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Play dynamic matches to win cash codes!", 
                            color = Color.Gray, 
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(matches) { match ->
                        UserMatchHistoryRow(match)
                    }
                }
            }
        } else {
            // Redeem History Tab
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("🎁", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No balance redemptions yet.", 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Earn 1000 Points and claim high stakes cards!", 
                            color = Color.Gray, 
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(history) { req ->
                        RedeemHistoryRow(req)
                    }
                }
            }
        }
    }
}

@Composable
fun SpanTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color.Black)
            .border(1.dp, Color.DarkGray, RoundedCornerShape(30.dp))
            .padding(4.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val isActive = idx == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (isActive) Color.DarkGray else Color.Transparent)
                    .clickable { onTabSelected(idx) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.uppercase(Locale.getDefault()),
                    color = if (isActive) Color.White else Color.Gray,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun UserMatchHistoryRow(match: MatchRecordFirebase) {
    val dateStr = try {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(match.timestamp))
    } catch(e: Exception) {
         ""
    }

    val outcomeColor = when (match.outcome) {
        "WIN" -> Color.White
        "LOSS" -> Color.White
        else -> Color.LightGray
    }

    val iconBg = when (match.outcome) {
        "WIN" -> Color.White.copy(alpha = 0.1f)
        "LOSS" -> Color.White.copy(alpha = 0.1f)
        else -> Color.LightGray.copy(alpha = 0.1f)
    }

    val badgeEmoji = when (match.outcome) {
        "WIN" -> "🏆"
        "LOSS" -> "💀"
        else -> "🤝"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = badgeEmoji, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Challenge Battle",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = dateStr,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = match.outcome,
                    color = outcomeColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
                Text(
                    text = if (match.pointsChange >= 0) "+${match.pointsChange} PTS" else "${match.pointsChange} PTS",
                    color = if (match.pointsChange >= 0) Color.White else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun RedeemHistoryRow(req: RedeemRequest) {
    val isPending = req.status == "PENDING"
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "₹${req.amount} Play Store Code",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    val dateFormatted = remember(req.timestamp) {
                        try {
                            SimpleDateFormat("dd MMM, yyyy h:mm a", Locale.getDefault()).format(Date(req.timestamp))
                        } catch (e: Exception) {
                            "Just now"
                        }
                    }
                    Text(text = dateFormatted, color = Color.Gray, fontSize = 11.sp)
                }

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            if (isPending) Color.Gray.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPending) "PENDING" else "COMPLETED",
                        color = if (isPending) Color.Gray else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            if (!isPending && req.redeemCode.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = req.redeemCode,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                    
                    Text(
                        text = "COPY",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Redeem Code", req.redeemCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Redeem card copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

// --- SCREEN 4: ADMIN PANEL DASHBOARD ---
@Composable
fun AdminPanelScreen(viewModel: TicTacToeViewModel) {
    val reqs by viewModel.allRedeemRequests.collectAsState()
    val matches by viewModel.allMatchHistory.collectAsState()
    val isLoading by viewModel.isAdminLoading.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) } // 0: Pending, 1: Winnings History

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ADMIN PANEL",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.loadAdminData() }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
            }
        }

        // Admin Profile Points Management
        val myPoints by viewModel.userPoints.collectAsState()
        var pointsInput by remember { mutableStateOf("") }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🛡️ ADMIN TOKEN POWER",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add or deduct points dynamically to your profile.",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "ADMIN BALANCE", color = Color.Gray, fontSize = 10.sp)
                        Text(text = "$myPoints PTS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pointsInput,
                            onValueChange = { pointsInput = it },
                            placeholder = { Text("Pts", color = Color.Gray) },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedContainerColor = Color.Black,
                                unfocusedContainerColor = Color.Black
                            ),
                            modifier = Modifier.width(90.dp).height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                val pts = pointsInput.toIntOrNull()
                                if (pts != null) {
                                    viewModel.adminAddPoints(pts, context)
                                    pointsInput = ""
                                } else {
                                    Toast.makeText(context, "Enter a valid integer!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(50.dp)
                        ) {
                            Text("ADD", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Segment Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 0 }
                    .background(if (selectedTab == 0) Color.DarkGray else Color.Transparent)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "WITHDRAWALS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (selectedTab == 0) Color.White else Color.White
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 1 }
                    .background(if (selectedTab == 1) Color.DarkGray else Color.Transparent)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "WINNINGS LOGS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (selectedTab == 1) Color.White else Color.White
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                if (selectedTab == 0) {
                    val listPending = reqs.filter { it.status == "PENDING" }
                    if (listPending.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No pending withdrawals requested.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(listPending) { reqObj ->
                            AdminRequestItem(reqObj) { codeValue ->
                                viewModel.approveWithdrawRequest(reqObj.requestId, codeValue, context)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                } else {
                    val logs = matches
                    if (logs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No match winning logs registered.", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(logs) { itemMatch ->
                            AdminMatchRecordRow(itemMatch)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminRequestItem(req: RedeemRequest, onSubmitCode: (String) -> Unit) {
    var codeText by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.DarkGray, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "USER: ${req.email}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Points claim: ${req.pointsRedeemed} PTS", fontSize = 12.sp, color = Color.Gray)
                Text(text = "Amount: ₹${req.amount}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = codeText,
                onValueChange = { codeText = it },
                label = { Text("Paste Play Store Redeem Code here", color = Color.Gray, fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedContainerColor = Color.Black,
                    unfocusedContainerColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { onSubmitCode(codeText) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("SUBMIT CODEPASS", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AdminMatchRecordRow(record: MatchRecordFirebase) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = record.email, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                val dateStr = remember(record.timestamp) {
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(record.timestamp))
                }
                Text(text = dateStr, color = Color.Gray, fontSize = 11.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = record.outcome,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = if (record.outcome == "WIN") Color.White else Color.White
                )
                Text(
                    text = if (record.pointsChange > 0) "+${record.pointsChange} PTS" else "${record.pointsChange} PTS",
                    color = if (record.pointsChange > 0) Color.White else Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// --- SCREEN 5: COUNTER RADAR QUEUE ---
@Composable
fun MatchmakingScreen(viewModel: TicTacToeViewModel) {
    val duration by viewModel.matchmakingTimeSec.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MATCH QUEUE ENEMY DETECT",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Gray,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(180.dp)
                .border(1.5.dp, Color.DarkGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .border(1.dp, Color.DarkGray, CircleShape)
            )

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .rotate(sweepRotation),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.Gray, CircleShape)
                )
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.Black, CircleShape)
                    .border(1.5.dp, Color.Gray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "⚔️", fontSize = 22.sp)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "Seconds Elapsed: ${duration}s",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Awaiting available AI lobby connection...",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

// --- SCREEN 6: VERSUS CARDS DISPLAY ---
@Composable
fun MatchFoundCards(viewModel: TicTacToeViewModel) {
    val email by viewModel.userEmail.collectAsState()
    val opponent by viewModel.opponent.collectAsState()
    val points by viewModel.userPoints.collectAsState()

    val context = LocalContext.current
    var displayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        displayed = true
        delay(2800)
        val act = context.findActivity()
        if (act != null) {
            AdManager.showRewarded(
                activity = act,
                onRewardEarned = {},
                onDismiss = {
                    viewModel.setupNewGame()
                }
            )
        } else {
            viewModel.setupNewGame()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SERVER CONNECTED!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(44.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player side
            AnimatedVisibility(
                visible = displayed,
                enter = slideInHorizontally(animationSpec = tween(500)) { -it } + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(Brush.linearGradient(listOf(Color.Gray, Color.Gray)), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👤", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = email?.split("@")?.get(0) ?: "You",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$points PTS",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            // VS Circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "VS", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color.White)
            }

            // Masked Opponent
            AnimatedVisibility(
                visible = displayed,
                enter = slideInHorizontally(animationSpec = tween(500)) { it } + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(opponent.avatarBg, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = opponent.avatarEmoji, fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // Mask opponent name with stars! (Satisfies requirements)
                    Text(
                        text = maskOpponentName(opponent.name),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${opponent.rankTitle} (${opponent.winRate} WR)",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Match started dynamically. Winning grants +300 PTS. Play perfectly!",
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// --- SCREEN 7: GAMEPLAY ARENA (NO CHAT) ---
@Composable
fun GameplayScreen(viewModel: TicTacToeViewModel) {
    val email by viewModel.userEmail.collectAsState()
    val opponent by viewModel.opponent.collectAsState()
    val board by viewModel.board.collectAsState()
    val isTurn by viewModel.isPlayerTurn.collectAsState()
    val activeTime by viewModel.turnTimer.collectAsState()
    val isThinking by viewModel.isOpponentThinking.collectAsState()
    val ping by viewModel.pingMs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ping bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(Color.White, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                // Mask the opponent's name so user feels opponent is real but hidden
                Text(
                    text = "Playing: ${maskOpponentName(opponent.name)}",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Ping: ${ping}ms",
                fontSize = 11.sp,
                color = if (ping < 50) Color.White else Color.White
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Players Heads HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player Left
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).graphicsLayer(alpha = if (isTurn) 1f else 0.45f)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color.Gray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = email?.split("@")?.get(0) ?: "Player",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("X SIGN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }

            // Intermediary timer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isMyTurn(isTurn, isThinking)) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, if (isMyTurn(isTurn, isThinking)) Color.White else Color.White, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${activeTime}s",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (isMyTurn(isTurn, isThinking)) Color.White else Color.White
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isMyTurn(isTurn, isThinking)) "YOUR MOVE" else "WAITING",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            // Masked Opponent Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f).graphicsLayer(alpha = if (!isMyTurn(isTurn, isThinking)) 1f else 0.45f)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = maskOpponentName(opponent.name),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("O SIGN", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(36.dp).background(opponent.avatarBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(opponent.avatarEmoji, fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Thinking HUD overlay
        AnimatedVisibility(visible = isThinking) {
            Row(
                modifier = Modifier
                    .background(Color.DarkGray, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(12.dp), strokeWidth = 1.8.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Opponent is drafting move...", color = Color.LightGray, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic playgrid board
        GameBoardGrid(board = board, isMyTurn = isTurn && !isThinking, onSelectCell = { cellIndex ->
            viewModel.makePlayerMove(cellIndex)
        })

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "No in-game chatting permitted during tournaments.",
            color = Color.DarkGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}

fun isMyTurn(isTurn: Boolean, isThinking: Boolean): Boolean {
    return isTurn && !isThinking
}

@Composable
fun GameBoardGrid(
    board: List<String>,
    isMyTurn: Boolean,
    onSelectCell: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .aspectRatio(1f)
            .background(Color.Black, RoundedCornerShape(20.dp))
            .border(2.dp, Color.DarkGray, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..2) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        val symbolMark = board[index]

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (symbolMark.isEmpty()) Color.DarkGray else Color.DarkGray)
                                .border(
                                    1.dp,
                                    if (symbolMark == "X") Color.White.copy(alpha = 0.5f)
                                    else if (symbolMark == "O") Color.White.copy(alpha = 0.5f)
                                    else Color.DarkGray,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(
                                    enabled = symbolMark.isEmpty() && isMyTurn,
                                    onClick = { onSelectCell(index) }
                                )
                                .testTag("cell_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = symbolMark,
                                transitionSpec = { scaleIn() togetherWith fadeOut() },
                                label = "BoardCellReveal"
                            ) { mark ->
                                when (mark) {
                                    "X" -> Text("X", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    "O" -> Text("O", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    else -> Box(modifier = Modifier.size(1.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- SCREEN 8: GAME OVER TERMINATION ---
@Composable
fun GameOverScreen(viewModel: TicTacToeViewModel) {
    val winner by viewModel.winner.collectAsState()
    val opponent by viewModel.opponent.collectAsState()

    val isWin = winner == "X"
    val isDraw = winner == "DRAW"

    val headerText = if (isWin) "VICTORY MATCH!" else if (isDraw) "MATCH TIED" else "DEFEAT"
    val colorAccent = if (isWin) Color.White else if (isDraw) Color.Gray else Color.White

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color.Black, Color.Black)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(colorAccent.copy(alpha = 0.1f), CircleShape)
                .border(2.dp, colorAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (isWin) "🏆" else if (isDraw) "🤝" else "💥", fontSize = 48.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = headerText,
            color = colorAccent,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tournament Versus: ${maskOpponentName(opponent.name)}",
            color = Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.DarkGray, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "POINT PAYOUT STATEMENT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isWin) "+300 POINTS" else if (isDraw) "0 POINTS" else "-50 POINTS",
                    color = if (isWin) Color.White else if (isDraw) Color.White else Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isWin) "Perfect gameplay! Bonus balance saved instantly to your profile." 
                    else if (isDraw) "Draw has resolved. Reposition yourself to seal victory next!" 
                    else "Slight fatfinger mistake cost 50 points. Watch rewarded videos for fast replenishment!",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Play Again Button (Requires ads before playing again)
        val context = LocalContext.current
        Button(
            onClick = {
                val act = context.findActivity()
                if (act != null) {
                    viewModel.playAgain(act)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("PLAY CHALLENGE AGAIN", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Exit to main home
        OutlinedButton(
            onClick = { 
                val act = context.findActivity()
                viewModel.exitToHome(act) 
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.DarkGray),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("EXIT TO HOME ARENA", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}
