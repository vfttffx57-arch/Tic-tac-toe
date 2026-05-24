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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.FullScreenContentCallback
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
        avatarBg = Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))),
        tagline = "Let's make this a beautiful game! 🌸"
    ),
    OpponentProfile(
        name = "Alex_TicTac_🎮",
        avatarEmoji = "👾",
        rankTitle = "Gold I",
        winRate = "54%",
        avatarBg = Brush.linearGradient(listOf(Color(0xFF00FF87), Color(0xFF60EFFF))),
        tagline = "Down for a chill session. No tryhards! 🥤"
    ),
    OpponentProfile(
        name = "CrossMaster_X",
        avatarEmoji = "⚡",
        rankTitle = "Gold IV",
        winRate = "49%",
        avatarBg = Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))),
        tagline = "Road to Platinum! Don't lag please. 😤"
    ),
    OpponentProfile(
        name = "BtrFingers_🍿",
        avatarEmoji = "🍕",
        rankTitle = "Silver II",
        winRate = "46%",
        avatarBg = Brush.linearGradient(listOf(Color(0xFFF12711), Color(0xFFF5AF19))),
        tagline = "typing from phone while eating pizza sry"
    ),
    OpponentProfile(
        name = "ZenPanda_🐼",
        avatarEmoji = "🎋",
        rankTitle = "Gold II",
        winRate = "50%",
        avatarBg = Brush.linearGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d))),
        tagline = "Focus like a mountain, flow like water. 🧘"
    )
)

// Opponent name obfuscation utility: E.g., "LunaStar_✨" -> "🤖 Lu***ar_✨"
fun maskOpponentName(name: String): String {
    val prefix = "🤖 "
    val suffix = if (name.endsWith("✨") || name.endsWith("🎮") || name.endsWith("🍿") || name.endsWith("🐼")) {
        "_" + name.takeLast(1)
    } else ""
    
    val cleanName = name.replace("🤖 ", "").replace("✨", "").replace("🎮", "").replace("🍿", "").replace("🐼", "").trim()
    if (cleanName.length <= 4) {
         return prefix + cleanName.take(1) + "***" + cleanName.takeLast(1) + suffix
    }
    return prefix + cleanName.take(2) + "***" + cleanName.takeLast(2) + suffix
}

// --- VIEW MODEL ---
class TicTacToeViewModel : ViewModel() {

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

    private val _allMatchHistory = MutableStateFlow<List<MatchRecordFirebase>>(emptyList())
    val allMatchHistory: StateFlow<List<MatchRecordFirebase>> = _allMatchHistory.asStateFlow()

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
            _currentScreen.value = ScreenState.HOME
            refreshUserProfile()
            loadRedeemHistory()
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
                } else {
                    // Initialize first if profile failed to fetch
                    FirebaseService.saveUserProfile(result.uid, result.email, 500)
                    _userPoints.value = 500
                }

                _currentScreen.value = ScreenState.HOME
                loadRedeemHistory()
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

                // Register 500 Points default welcome balance in Cloud Database
                val saveOk = FirebaseService.saveUserProfile(result.uid, result.email, 500)
                _userPoints.value = 500

                _currentScreen.value = ScreenState.HOME
                loadRedeemHistory()
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
        // Show Google Play Rewarded Ad before joining Match Queue!
        AdManager.showRewarded(
            activity = activity,
            onRewardEarned = {
                // Earned reward
            },
            onDismiss = {
                // Instantly queues user into matchmaking
                startMatchmaking()
            }
        )
    }

    fun startMatchmaking() {
        _currentScreen.value = ScreenState.MATCHMAKING
        _matchmakingTimeSec.value = 0
        viewModelScope.launch {
            for (i in 1..4) {
                delay(1000)
                _matchmakingTimeSec.value = i
            }
            // Pick a random styled AI opponent
            _opponent.value = OPPONENTS_POOL.random()
            _currentScreen.value = ScreenState.MATCH_FOUND
            delay(2800) // Beautiful versus card animation pause
            setupNewGame()
        }
    }

    private fun setupNewGame() {
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

        // 30% chance of a slight strategic error to allow player to win, keeping it fun!
        val selectedMove = if ((1..100).random() <= 30) {
            val available = currentBoard.indices.filter { currentBoard[it].isEmpty() }
            if (available.isNotEmpty()) available.random() else -1
        } else {
            selectComputerMove(currentBoard, playerSym, compSym)
        }

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

            // Record outcome to match logs
            val outcome = if (isWin) "WIN" else if (isDraw) "DRAW" else "LOSS"
            FirebaseService.recordMatch(uid, email, outcome, pointsChange)

            delay(1200)
            _currentScreen.value = ScreenState.GAMEOVER
        }
    }

    fun playAgain() {
        startMatchmaking()
    }

    fun exitToHome() {
        refreshUserProfile()
        loadRedeemHistory()
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

class TicTacToeViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TicTacToeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TicTacToeViewModel() as T
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
                val viewModel: TicTacToeViewModel = viewModel(factory = TicTacToeViewModelFactory())

                // Checking if user is pre-authenticated
                LaunchedEffect(Unit) {
                    viewModel.checkAutoLogin(context)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0E0D16) // Cosmic slate dark base color
                ) {
                    AppScreenContainer(viewModel)
                }
            }
        }
    }
}

// --- GOOGLE ADMOB AD MANAGER ---
object AdManager {
    var BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    var INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    var REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    var isInitialized = false
    var isRewardedLoading = false
    private var mRewardedAd: RewardedAd? = null

    fun init(context: android.content.Context) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        Log.d("AdManager", "Initializing Google Mobile Ads")
        try {
            MobileAds.initialize(context) {
                mainHandler.post {
                    isInitialized = true
                    Log.d("AdManager", "Initialization complete")
                    loadRewarded(context)
                }
            }
        } catch (e: Exception) {
            Log.e("AdManager", "Init crash: ${e.message}")
        }
    }

    fun loadRewarded(context: android.content.Context) {
        if (isRewardedLoading) return
        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(rewardedAd: RewardedAd) {
                mRewardedAd = rewardedAd
                isRewardedLoading = false
                Log.d("AdManager", "Admob Rewarded loaded successfully")
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                mRewardedAd = null
                isRewardedLoading = false
                Log.e("AdManager", "Admob Rewarded failed to load: ${loadAdError.message}")
            }
        })
    }

    fun showRewarded(activity: android.app.Activity, onRewardEarned: (Int) -> Unit, onDismiss: () -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            val ad = mRewardedAd
            if (ad != null) {
                var rewardGiven = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        mRewardedAd = null
                        loadRewarded(activity)
                        if (rewardGiven) {
                            onDismiss()
                        } else {
                            // User skipped/closed ad -> Backup Simulated
                            showSimulatedSponsorAd(activity, onRewardEarned, onDismiss)
                        }
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                        mRewardedAd = null
                        loadRewarded(activity)
                        showSimulatedSponsorAd(activity, onRewardEarned, onDismiss)
                    }
                }
                ad.show(activity, OnUserEarnedRewardListener { rewardItem ->
                    rewardGiven = true
                    onRewardEarned(rewardItem.amount)
                })
            } else {
                // If live AdMob not cached/ready, show backup 5-sec sponsoring Dialog immediately!
                showSimulatedSponsorAd(activity, onRewardEarned, onDismiss)
            }
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
                    .background(Color(0xFF0E0D16)),
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
                                    .background(if (isCompleted) Color(0xFF00FF87) else Color(0xFFFF2E93))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GOOGLE PLAY SPONSOR NETWORK",
                                color = Color(0xFF8B8A9D),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isCompleted) Color(0xFF00FF87).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isCompleted) "✓ REWARDED" else "Reward in ${timeLeft}s",
                                color = if (isCompleted) Color(0xFF00FF87) else Color.White,
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
                            .border(1.dp, Color(0xFF2E2A4E), RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131124)),
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
                                    .background(Color(0xFF00FF87).copy(alpha = 0.08f))
                                    .border(1.dp, Color(0xFF00FF87).copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF00FF87),
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
                                color = Color(0xFF8B8A9D),
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
                            color = Color(0xFF00FF87),
                            trackColor = Color(0xFF1D1B36),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isCompleted) "✓ YOUR REWARD SECURED! Closing in 1s..." else "Validating backup ad impression... Do not close.",
                            fontSize = 11.sp,
                            color = if (isCompleted) Color(0xFF00FF87) else Color(0xFF8B8A9D),
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
    androidx.compose.ui.viewinterop.AndroidView<android.view.View>(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            val adView = AdView(context).apply {
                adUnitId = AdManager.BANNER_ID
                setAdSize(AdSize.BANNER)
                adListener = object : com.google.android.gms.ads.AdListener() {
                    override fun onAdLoaded() {
                        Log.d("AdmobBanner", "Banner loaded successfully")
                    }
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e("AdmobBanner", "Banner failed to load: ${loadAdError.message}")
                    }
                }
            }
            adView.loadAd(AdRequest.Builder().build())
            adView
        },
        update = { }
    )
}

// --- COMPOSE CORE SCREEN ROUTER ---
@Composable
fun AppScreenContainer(viewModel: TicTacToeViewModel) {
    val screenState by viewModel.currentScreen.collectAsState()

    AnimatedContent(
        targetState = screenState,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "RouterTransitions"
    ) { target ->
        when (target) {
            ScreenState.ONBOARDING -> FirebaseLoginSetupScreen(viewModel)
            ScreenState.HOME -> HomeDashboardScreen(viewModel)
            ScreenState.REDEEM -> RedeemScreen(viewModel)
            ScreenState.ADMIN -> AdminPanelScreen(viewModel)
            ScreenState.MATCHMAKING -> MatchmakingScreen(viewModel)
            ScreenState.MATCH_FOUND -> MatchFoundCards(viewModel)
            ScreenState.GAMEPLAY -> GameplayScreen(viewModel)
            ScreenState.GAMEOVER -> GameOverScreen(viewModel)
        }
    }
}

// --- SCREEN 1: FIREBASE LOGIN / REGISTRATION ---
@Composable
fun FirebaseLoginSetupScreen(viewModel: TicTacToeViewModel) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    
    val isLoading by viewModel.isLoginLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val context = LocalContext.current

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF141221), Color(0xFF0D0A14))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF00FF87), Color.Transparent)), CircleShape)
                .wrapContentSize(Alignment.Center)
        ) {
            Text(text = "⚔️", fontSize = 38.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tic Tac Toe",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 1.sp
        )

        Text(
            text = "CHAMPIONS PLAY ARENA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00FF87),
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF19162B)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2C274E), RoundedCornerShape(20.dp))
                .shadow(8.dp, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignUpMode) "CREATE NEW ACCOUNT" else "SECURE MEMBERS SIGN IN",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Email Input
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Email Address", color = Color(0xFF8B8A9D)) },
                    placeholder = { Text("E.g., user@gmail.com", color = Color(0xFF5A547C)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF2C274E),
                        focusedContainerColor = Color(0xFF110E1E),
                        unfocusedContainerColor = Color(0xFF110E1E)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password Input
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password", color = Color(0xFF8B8A9D)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF2C274E),
                        focusedContainerColor = Color(0xFF110E1E),
                        unfocusedContainerColor = Color(0xFF110E1E)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (authError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = authError ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFF00FF87))
                } else {
                    Button(
                        onClick = {
                            if (isSignUpMode) {
                                viewModel.signUp(emailInput, passwordInput, context)
                            } else {
                                viewModel.login(emailInput, passwordInput, context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (isSignUpMode) "SIGN UP NOW" else "SIGN IN SECURELY",
                            color = Color(0xFF040306),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isSignUpMode) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                        color = Color(0xFF00FF87),
                        fontSize = 12.sp,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0A12))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top HUD Bar showing email and current Points
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141221))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = email ?: "Player Session",
                    fontSize = 11.sp,
                    color = Color(0xFF8B8A9D),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "STATUS: ONLINE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF87)
                )
            }

            // Beautiful Top Bar Points Display
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xFF1E1A33))
                    .border(1.dp, Color(0xFF00FF87).copy(alpha = 0.4f), RoundedCornerShape(30.dp))
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16132D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .border(1.dp, Color(0xFF2C2652), RoundedCornerShape(16.dp))
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
                            color = Color(0xFF8B8A9D)
                        )
                    }
                }
            }

            // Large Play Button Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1340)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable {
                        if (activity != null) {
                            viewModel.startPlayMatchFlow(activity)
                        }
                    }
                    .border(2.dp, Color(0xFF00FF87).copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
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
                            .background(Color(0xFF00FF87).copy(alpha = 0.1f), CircleShape)
                            .border(1.5.dp, Color(0xFF00FF87), CircleShape),
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
                        color = Color(0xFFBDC2E8),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (activity != null) {
                                viewModel.startPlayMatchFlow(activity)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "START VS AI arena", color = Color(0xFF09080E), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Earn Free Points card option
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF110E20)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable {
                        if (activity != null) {
                            viewModel.watchRewardedAdForPoints(activity)
                        }
                    }
                    .border(1.dp, Color(0xFF241F3C), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "💎", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("FREE +150 POINTS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Support us and claim bonus points", color = Color(0xFF8B8A9D), fontSize = 11.sp)
                        }
                    }
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF00FF87))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Card: REDEEM POINTS
            Button(
                onClick = {
                    viewModel.loadRedeemHistory()
                    viewModel.refreshUserProfile()
                    viewModel.setScreen(ScreenState.REDEEM)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7000FF)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "REDEEM PLAY STORE GIFT CODES", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Admin Link Option visible only for admin email
            if (email == "vfttffx57@gmail.com") {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.loadAdminData()
                        viewModel.setScreen(ScreenState.ADMIN)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "ENTER ADMIN DASHBOARD", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AdmobBanner(modifier = Modifier.padding(vertical = 4.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "SIGN OUT SECTION",
                color = Color(0xFFFF5252),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { viewModel.logout(context) }
                    .padding(8.dp)
            )
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
            .background(Color(0xFF0C0A12))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141221))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.setScreen(ScreenState.HOME) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1638)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF332A6B), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "CURRENT REDEEMABLE BALANCE", color = Color(0xFFBDC2E8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(text = "⭐ $points PTS", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Text(text = "Value: ₹${(points / 100.0).format(2)} INR", color = Color(0xFF00FF87), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161524)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF28263D), RoundedCornerShape(16.dp))
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
                                    Text("Costs: 1000 Points", color = Color(0xFF8B8A9D), fontSize = 11.sp)
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.claimRedeemRequest(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
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
                            .background(Color(0xFF141322), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No redemptions submitted yet.", color = Color(0xFF5E5C70), fontSize = 12.sp)
                    }
                }
            } else {
                items(history) { req ->
                    RedeemHistoryRow(req)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        AdmobBanner(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}

// Float output formatter safely
fun Double.format(digits: Int) = java.lang.String.format("%.${digits}f", this)

@Composable
fun RedeemHistoryRow(req: RedeemRequest) {
    val isPending = req.status == "PENDING"
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141322)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF211F36), RoundedCornerShape(12.dp))
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
                    Text(text = dateFormatted, color = Color(0xFF5C5A75), fontSize = 11.sp)
                }

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            if (isPending) Color(0xFFFF9800).copy(alpha = 0.12f) else Color(0xFF00FF87).copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isPending) "PENDING" else "COMPLETED",
                        color = if (isPending) Color(0xFFFF9800) else Color(0xFF00FF87),
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
                        .background(Color(0xFF0F0E1B), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = req.redeemCode,
                        color = Color(0xFF00FF87),
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
            .background(Color(0xFF0C0A12))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141221))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.setScreen(ScreenState.HOME) }) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "ADMIN PANEL",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.loadAdminData() }) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF00FF87))
            }
        }

        // Segment Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF18152D))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 0 }
                    .background(if (selectedTab == 0) Color(0xFF282449) else Color.Transparent)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "WITHDRAWALS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (selectedTab == 0) Color(0xFF00FF87) else Color.White
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedTab = 1 }
                    .background(if (selectedTab == 1) Color(0xFF282449) else Color.Transparent)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "WINNINGS LOGS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (selectedTab == 1) Color(0xFF00FF87) else Color.White
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00FF87))
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
                                Text("No pending withdrawals requested.", color = Color(0xFF6E6B8D), fontSize = 13.sp)
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
                                Text("No match winning logs registered.", color = Color(0xFF6E6B8D), fontSize = 13.sp)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161524)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2B2846), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "USER: ${req.email}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Points claim: ${req.pointsRedeemed} PTS", fontSize = 12.sp, color = Color(0xFF8B8A9D))
                Text(text = "Amount: ₹${req.amount}", fontSize = 12.sp, color = Color(0xFF00FF87), fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = codeText,
                onValueChange = { codeText = it },
                label = { Text("Paste Play Store Redeem Code here", color = Color(0xFF8B8A9D), fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00FF87),
                    unfocusedBorderColor = Color(0xFF2C274E),
                    focusedContainerColor = Color(0xFF0B0A11),
                    unfocusedContainerColor = Color(0xFF0B0A11)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { onSubmitCode(codeText) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141322)),
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
                Text(text = dateStr, color = Color(0xFF5C5A75), fontSize = 11.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = record.outcome,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    color = if (record.outcome == "WIN") Color(0xFF00FF87) else Color(0xFFFF5252)
                )
                Text(
                    text = if (record.pointsChange > 0) "+${record.pointsChange} PTS" else "${record.pointsChange} PTS",
                    color = if (record.pointsChange > 0) Color(0xFF00FF87) else Color(0xFFFF5252),
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
            .background(Color(0xFF0D0C13))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MATCH QUEUE ENEMY DETECT",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF7000FF),
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(180.dp)
                .border(1.5.dp, Color(0xFF1E1A2E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .border(1.dp, Color(0xFF2C2646), CircleShape)
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
                        .background(Color(0xFF7000FF), CircleShape)
                )
            }

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFF1A152B), CircleShape)
                    .border(1.5.dp, Color(0xFF7000FF), CircleShape),
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
            color = Color(0xFF8B8A9D),
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

    var displayed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        displayed = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09080E))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SERVER CONNECTED!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF00FF87),
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
                            .background(Brush.linearGradient(listOf(Color(0xFF7000FF), Color(0xFF4A00E0))), CircleShape)
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
                        color = Color(0xFF8B8A9D),
                        fontSize = 11.sp
                    )
                }
            }

            // VS Circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFFF5252), CircleShape)
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
                        color = Color(0xFF8B8A9D),
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Match started dynamically. Winning grants +300 PTS. Play perfectly!",
            fontSize = 12.sp,
            color = Color(0xFFFFD700),
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
            .background(Color(0xFF0C0A12))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ping bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141221))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).background(Color(0xFF00FF87), CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                // Mask the opponent's name so user feels opponent is real but hidden
                Text(
                    text = "Playing: ${maskOpponentName(opponent.name)}",
                    fontSize = 11.sp,
                    color = Color(0xFF8E8BB1),
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Ping: ${ping}ms",
                fontSize = 11.sp,
                color = if (ping < 50) Color(0xFF00FF87) else Color(0xFFFFD700)
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
                    modifier = Modifier.size(36.dp).background(Color(0xFF7000FF), CircleShape),
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
                    Text("X SIGN", color = Color(0xFF00FF87), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }

            // Intermediary timer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isMyTurn(isTurn, isThinking)) Color(0xFF00FF87).copy(alpha = 0.12f) else Color(0xFFFF5252).copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, if (isMyTurn(isTurn, isThinking)) Color(0xFF00FF87) else Color(0xFFFF5252), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${activeTime}s",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (isMyTurn(isTurn, isThinking)) Color(0xFF00FF87) else Color(0xFFFF5252)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isMyTurn(isTurn, isThinking)) "YOUR MOVE" else "WAITING",
                    fontSize = 9.sp,
                    color = Color(0xFF8B8A9D),
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
                    Text("O SIGN", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
                    .background(Color(0xFF1B192E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Color(0xFF00FF87), modifier = Modifier.size(12.dp), strokeWidth = 1.8.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Opponent is drafting move...", color = Color(0xFFBDC2E8), fontSize = 12.sp)
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
            color = Color(0xFF454359),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        AdmobBanner(modifier = Modifier.padding(bottom = 12.dp))
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
            .background(Color(0xFF12101F), RoundedCornerShape(20.dp))
            .border(2.dp, Color(0xFF23203C), RoundedCornerShape(20.dp))
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
                                .background(if (symbolMark.isEmpty()) Color(0xFF1A182E) else Color(0xFF23203D))
                                .border(
                                    1.dp,
                                    if (symbolMark == "X") Color(0xFF00FF87).copy(alpha = 0.5f)
                                    else if (symbolMark == "O") Color(0xFFFF5252).copy(alpha = 0.5f)
                                    else Color(0xFF2D2A47),
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
                                    "X" -> Text("X", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF87))
                                    "O" -> Text("O", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
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
    val colorAccent = if (isWin) Color(0xFF00FF87) else if (isDraw) Color(0xFF8B8A9D) else Color(0xFFFF5252)

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF120E22), Color(0xFF090710))
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
            color = Color(0xFFBDC2E8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161427)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2E2A4D), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "POINT PAYOUT STATEMENT", color = Color(0xFF8C86AA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isWin) "+300 POINTS" else if (isDraw) "0 POINTS" else "-50 POINTS",
                    color = if (isWin) Color(0xFF00FF87) else if (isDraw) Color.White else Color(0xFFFF5252),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isWin) "Perfect gameplay! Bonus balance saved instantly to your profile." 
                    else if (isDraw) "Draw has resolved. Reposition yourself to seal victory next!" 
                    else "Slight fatfinger mistake cost 50 points. Watch rewarded videos for fast replenishment!",
                    color = Color(0xFF8B8A9D),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Play Again Button (Directly queues user again)
        Button(
            onClick = { viewModel.playAgain() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
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
            onClick = { viewModel.exitToHome() },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF2E2A4D)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("EXIT TO HOME ARENA", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        AdmobBanner()
    }
}
