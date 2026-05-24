package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.UnityBannerSize
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- DATA STRUCTURES & OPPONENT SELECTION ---

enum class ScreenState {
    ONBOARDING,
    HOME,
    MATCHMAKING,
    MATCH_FOUND,
    GAMEPLAY,
    GAMEOVER
}

enum class PersonalityType {
    FRIENDLY,    // Emotes, polite, praises, sweet excuses
    CASUAL,      // "oof", "lol", chill gaming phrases, easygoing
    COMPETITIVE, // Throws excuses like "lag", "latency", competitive banter
    SNEAKY_TYPO  // Types with funny typos, blames fat fingers
}

data class OpponentProfile(
    val name: String,
    val avatarEmoji: String,
    val rankTitle: String,
    val winRate: String,
    val rankPoints: Int,
    val personality: PersonalityType,
    val avatarBg: Brush,
    val tagline: String
)

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val sender: String,
    val text: String,
    val isPlayer: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

val OPPONENTS_POOL = listOf(
    OpponentProfile(
        name = "LunaStar_✨",
        avatarEmoji = "💫",
        rankTitle = "Gold III",
        winRate = "51%",
        rankPoints = 1120,
        personality = PersonalityType.FRIENDLY,
        avatarBg = Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))),
        tagline = "Let's make this a beautiful game! 🌸"
    ),
    OpponentProfile(
        name = "Alex_TicTac_🎮",
        avatarEmoji = "👾",
        rankTitle = "Gold I",
        winRate = "54%",
        rankPoints = 1380,
        personality = PersonalityType.CASUAL,
        avatarBg = Brush.linearGradient(listOf(Color(0xFF00FF87), Color(0xFF60EFFF))),
        tagline = "Down for a chill session. No tryhards! 🥤"
    ),
    OpponentProfile(
        name = "CrossMaster_X",
        avatarEmoji = "⚡",
        rankTitle = "Gold IV",
        winRate = "49%",
        rankPoints = 1250,
        personality = PersonalityType.COMPETITIVE,
        avatarBg = Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B))),
        tagline = "Road to Platinum! Don't lag please. 😤"
    ),
    OpponentProfile(
        name = "BtrFingers_🍿",
        avatarEmoji = "🍕",
        rankTitle = "Silver II",
        winRate = "46%",
        rankPoints = 1040,
        personality = PersonalityType.SNEAKY_TYPO,
        avatarBg = Brush.linearGradient(listOf(Color(0xFFF12711), Color(0xFFF5AF19))),
        tagline = "typing from phone while eating pizza sry"
    ),
    OpponentProfile(
        name = "ZenPanda_🐼",
        avatarEmoji = "🎋",
        rankTitle = "Gold II",
        winRate = "50%",
        rankPoints = 1430,
        personality = PersonalityType.FRIENDLY,
        avatarBg = Brush.linearGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d))),
        tagline = "Calm breaths, perfect placements. 🌸"
    ),
    OpponentProfile(
        name = "RogueGamer_💀",
        avatarEmoji = "🎯",
        rankTitle = "Platinum V",
        winRate = "57%",
        rankPoints = 1620,
        personality = PersonalityType.COMPETITIVE,
        avatarBg = Brush.linearGradient(listOf(Color(0xFF1F1C2C), Color(0xFF928DAB))),
        tagline = "Rank matches only. Win streaks on lock."
    )
)

// --- VIEWMODEL ---

class TicTacToeViewModel(private val repository: GameRepository) : ViewModel() {

    // Profile & Stats from Database
    val playerProfile: StateFlow<PlayerProfile?> = repository.playerProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val matchHistory: StateFlow<List<MatchRecord>> = repository.matchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Screen States
    private val _currentScreen = MutableStateFlow(ScreenState.ONBOARDING)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    // Matchmaking variables
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

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isPingSimulating = MutableStateFlow(false)
    val isPingSimulating: StateFlow<Boolean> = _isPingSimulating.asStateFlow()

    private val _pingMs = MutableStateFlow(24)
    val pingMs: StateFlow<Int> = _pingMs.asStateFlow()

    private var timerJob: kotlinx.coroutines.Job? = null
    private var isRematching = false

    init {
        // Automatically move to HOME if nickname exists
        viewModelScope.launch {
            playerProfile.collect { profile ->
                if (profile != null && _currentScreen.value == ScreenState.ONBOARDING) {
                    _currentScreen.value = ScreenState.HOME
                }
            }
        }

        // Keep ping jumping naturally for realism
        viewModelScope.launch {
            while (true) {
                delay(1200)
                _pingMs.value = (22..68).random()
            }
        }
    }

    fun submitNickname(name: String) {
        viewModelScope.launch {
            val newProfile = PlayerProfile(
                username = if (name.trim().isEmpty()) "Player_${(1000..9999).random()}" else name.trim(),
                rankPoints = 1200, // Starts mid Gold
                wins = 0,
                losses = 0,
                draws = 0,
                walletBalance = 10.00 // Default initial wallet balance in Rupees
            )
            repository.saveProfile(newProfile)
            _currentScreen.value = ScreenState.HOME
        }
    }

    fun checkAndStartMatch(activity: android.app.Activity, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        val currentProfile = playerProfile.value
        if (currentProfile == null) {
            onFail("Profile not found")
            return
        }
        if (currentProfile.walletBalance < 0.10) {
            onFail("INSUFFICIENT_FUNDS")
            return
        }

        var earnedReward = false
        // Play requires watching a rewarded ad AND paying 0.10 rupees entry fee
        AdManager.showRewarded(
            activity = activity,
            onRewardEarned = { amount ->
                earnedReward = true
                viewModelScope.launch {
                    val nextProfile = currentProfile.copy(
                        walletBalance = (currentProfile.walletBalance - 0.10).coerceAtLeast(0.0)
                    )
                    repository.saveProfile(nextProfile)
                    onSuccess()
                }
            },
            onDismiss = {
                if (!earnedReward) {
                    onFail("AD_CLOSED")
                }
            }
        )
    }

    fun watchRewardedAdForTopUp(activity: android.app.Activity, onSuccess: () -> Unit, onFailure: () -> Unit) {
        var earnedReward = false
        AdManager.showRewarded(
            activity = activity,
            onRewardEarned = { amount ->
                earnedReward = true
                viewModelScope.launch {
                    val currentProfile = playerProfile.value ?: return@launch
                    val updatedProfile = currentProfile.copy(
                        walletBalance = currentProfile.walletBalance + 0.50
                    )
                    repository.saveProfile(updatedProfile)
                    onSuccess()
                }
            },
            onDismiss = {
                if (!earnedReward) {
                    onFailure()
                }
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
            // Pick a random opponent from the list
            _opponent.value = OPPONENTS_POOL.random()
            _currentScreen.value = ScreenState.MATCH_FOUND
            delay(2800) // Versus match cards display
            setupNewGame()
        }
    }

    private fun setupNewGame() {
        _board.value = List(9) { "" }
        _winner.value = null
        _chatMessages.value = emptyList()
        _currentScreen.value = ScreenState.GAMEPLAY

        // Randomly select who starts
        val playerStarts = (0..1).random() == 1
        _isPlayerTurn.value = playerStarts

        // Set up first opponent text
        viewModelScope.launch {
            delay(1000)
            sendOpponentInitialChat()
        }

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
                    // Force natural auto-move or passive stall if they run out of time
                    if (_isPlayerTurn.value) {
                        // Play random cell for player if AFK timer strikes
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

        // Check for immediate win
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
        _isPingSimulating.value = true
        viewModelScope.launch {
            // Artificial delay to mimic online speed (1 to 2.3 seconds)
            val artificialDelay = (1200..2300).random().toLong()
            delay(artificialDelay)

            if (_winner.value != null) return@launch

            makeOpponentMove()
            _isOpponentThinking.value = false
            _isPingSimulating.value = false

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

        val selectedMove = selectComputerMove(currentBoard, playerSym, compSym)
        if (selectedMove != -1) {
            val newBoard = currentBoard.toMutableList()
            newBoard[selectedMove] = compSym
            _board.value = newBoard

            // Chat reaction chance for making sub-optimal play or ignoring danger
            viewModelScope.launch {
                delay(400)
                // Determine if opponent missed a block or ignored warning
                val hasIgnoredBlock = hasMissedMajorBlock(currentBoard, playerSym, selectedMove)
                val hasIgnoredWin = hasMissedMajorWin(currentBoard, compSym, selectedMove)

                if (hasIgnoredBlock) {
                    sendOpponentReactionChat(ReasonForChat.MISSED_BLOCK)
                } else if (hasIgnoredWin) {
                    sendOpponentReactionChat(ReasonForChat.MISSED_WIN)
                } else if ((1..100).random() < 22) {
                    sendOpponentReactionChat(ReasonForChat.CASUAL_REACTION)
                }
            }
        }
    }

    // UNBEATABLE MINIMAX AI ALGORITHM
    // Ensures perfect logical play, sealing all pathways and never permitting the player to win.
    private fun selectComputerMove(board: List<String>, playerSym: String, compSym: String): Int {
        val availableIdx = board.indices.filter { board[it].isEmpty() }
        if (availableIdx.isEmpty()) return -1

        // If it's the first turn of the game, take a highly strategic position (center or corner)
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

        // Adjust scores for depth to prioritize quicker wins and slower losses
        if (score == 10) return score - depth
        if (score == -10) return score + depth

        // If there are no empty slots and no winner, it is a tie
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
            if (a.isNotEmpty() && a == b && a == c) {
                if (a == compSym) return 10
                if (a == playerSym) return -10
            }
        }
        return 0
    }

    private fun checkIfMoveWins(board: List<String>, idx: Int, symbol: String): Boolean {
        val tempBoard = board.toMutableList()
        tempBoard[idx] = symbol
        return checkWinnerState(tempBoard) == symbol
    }

    private fun doesMoveCreateTwo(board: List<String>, idx: Int, symbol: String): Boolean {
        val tempBoard = board.toMutableList()
        tempBoard[idx] = symbol
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (line in lines) {
            if (idx in line) {
                if (line.count { tempBoard[it] == symbol } == 2 && line.count { tempBoard[it].isEmpty() } == 1) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasMissedMajorBlock(board: List<String>, playerSym: String, playedIdx: Int): Boolean {
        // Did player have 2 in a row that we failed to block (played elsewhere)?
        val available = board.indices.filter { board[it].isEmpty() }
        val blocksAvailable = available.filter { checkIfMoveWins(board, it, playerSym) }
        return blocksAvailable.isNotEmpty() && playedIdx !in blocksAvailable
    }

    private fun hasMissedMajorWin(board: List<String>, compSym: String, playedIdx: Int): Boolean {
        // Did we have 2 in a row that we could have won but avoided?
        val available = board.indices.filter { board[it].isEmpty() }
        val winsAvailable = available.filter { checkIfMoveWins(board, it, compSym) }
        return winsAvailable.isNotEmpty() && playedIdx !in winsAvailable
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

        viewModelScope.launch {
            val isWin = winnerSymbol == "X"
            val isDraw = winnerSymbol == "DRAW"

            // Gained / lost rating calculations
            val currentProfile = playerProfile.value ?: return@launch
            val pointsChange = if (isWin) {
                (24..30).random()
            } else if (isDraw) {
                (1..4).random()
            } else {
                -((10..15).random()) // Highly unlikely they lose, but keep math full
            }

            // Create record
            val record = MatchRecord(
                opponentName = _opponent.value.name,
                opponentRank = _opponent.value.rankTitle,
                result = if (isWin) "WIN" else if (isDraw) "DRAW" else "LOSS",
                lpChange = pointsChange
            )
            repository.addMatchRecord(record)

            // Update local profile rating with max bounds and division updates
            val finalPoints = (currentProfile.rankPoints + pointsChange).coerceAtLeast(100)
            val updatedProfile = GameProfileUpgrade(currentProfile, isWin, isDraw)
            repository.saveProfile(updatedProfile.copy(rankPoints = finalPoints))

            // Short dynamic chat reaction from bot
            delay(1000)
            val endState = if (isWin) ReasonForChat.LOST_GAME else if (isDraw) ReasonForChat.TIED_GAME else ReasonForChat.WON_GAME
            sendOpponentReactionChat(endState)

            delay(1200)
            _currentScreen.value = ScreenState.GAMEOVER
        }
    }

    private fun GameProfileUpgrade(profile: PlayerProfile, isWin: Boolean, isDraw: Boolean): PlayerProfile {
        return profile.copy(
            wins = if (isWin) profile.wins + 1 else profile.wins,
            draws = if (isDraw) profile.draws + 1 else profile.draws,
            losses = if (!isWin && !isDraw) profile.losses + 1 else profile.losses,
            walletBalance = if (isWin) {
                profile.walletBalance + 0.18
            } else if (isDraw) {
                profile.walletBalance + 0.10 // Draw refunds the ₹0.10 entry fee
            } else {
                profile.walletBalance // Loss keeps the spent ₹0.10 entry fee (equals -₹0.10 net)
            }
        )
    }

    // --- INSTANT CHAT LOG CONTEXT & TRIGGERING ---

    fun sendPlayerChat(presetText: String) {
        val playerMsg = ChatMessage(sender = "You", text = presetText, isPlayer = true)
        _chatMessages.value = _chatMessages.value + playerMsg

        // Auto opponent response delay (under 1.5 seconds)
        viewModelScope.launch {
            delay((600..1300).random().toLong())
            val replyText = getOpponentReplyToPlayerPreset(presetText, _opponent.value.personality)
            val opponentMsg = ChatMessage(sender = _opponent.value.name, text = replyText, isPlayer = false)
            _chatMessages.value = _chatMessages.value + opponentMsg
        }
    }

    private fun sendOpponentInitialChat() {
        val greet = when(_opponent.value.personality) {
            PersonalityType.FRIENDLY -> listOf(
                "hi! good luck today! 💫",
                "hello! let's have a fun match 🌷",
                "hey! hope your session goes well!"
            ).random()
            PersonalityType.CASUAL -> listOf(
                "yo gl hf 🥤",
                "sup! ready to play?",
                "heyy gl"
            ).random()
            PersonalityType.COMPETITIVE -> listOf(
                "gl block me if you can lol",
                "yo! matchmaking took forever, let's go",
                "gl hope you're in my tier rank-wise"
            ).random()
            PersonalityType.SNEAKY_TYPO -> listOf(
                "hey glhhf",
                "sup gl m8",
                "hllo gl, hope I dont misclck"
            ).random()
        }
        val msg = ChatMessage(sender = _opponent.value.name, text = greet, isPlayer = false)
        _chatMessages.value = _chatMessages.value + msg
    }

    enum class ReasonForChat {
        MISSED_BLOCK,
        MISSED_WIN,
        CASUAL_REACTION,
        LOST_GAME,
        TIED_GAME,
        WON_GAME
    }

    private fun sendOpponentReactionChat(reason: ReasonForChat) {
        val style = _opponent.value.personality
        val text = when (reason) {
            ReasonForChat.MISSED_BLOCK -> when (style) {
                PersonalityType.FRIENDLY -> listOf("Oops, I forgot to block! 🙈", "Oh my bad, totally missed that cell ✨", "Ah! I was distracted by the pretty board.").random()
                PersonalityType.CASUAL -> listOf("wait didn't see that lol, brain lag", "lmaooo missed that block completely oof", "my focus went totally out of bounds there").random()
                PersonalityType.COMPETITIVE -> listOf("Wait lag? I clicked to block but nothing happened!", "Latency spike, that didn't snap correctly grr", "That registers so late! I swear I put it to block).").random()
                PersonalityType.SNEAKY_TYPO -> listOf("omg fta fingers sry didn block", "wiat I am literally blnid lol", "missd that row so bad rip").random()
            }
            ReasonForChat.MISSED_WIN -> when (style) {
                PersonalityType.FRIENDLY -> listOf("Wait, I missed my own double opportunity? Silly me! 💛", "Oh! I didn't mean to ignore that row!").random()
                PersonalityType.CASUAL -> listOf("bruh how did I miss my own win line 😂", "lmao eye vision checklist failed").random()
                PersonalityType.COMPETITIVE -> listOf("I saw it, but wanted to try a secondary triple line setup.", "Double threats only, regular win is too boring.").random()
                PersonalityType.SNEAKY_TYPO -> listOf("didnt see my onw line lol", "wite how did I missed that oof").random()
            }
            ReasonForChat.CASUAL_REACTION -> when (style) {
                PersonalityType.FRIENDLY -> listOf("Beautiful positioning! 🌸", "This board looks like a constellation ✨").random()
                PersonalityType.CASUAL -> listOf("shaping up to be a spicy match!", "nice placement").random()
                PersonalityType.COMPETITIVE -> listOf("hmmm clever move. Let's see your next turn.", "trying to box me in? classic.").random()
                PersonalityType.SNEAKY_TYPO -> listOf("clever movee!", "hmnm let me thnk").random()
            }
            ReasonForChat.LOST_GAME -> when (style) {
                PersonalityType.FRIENDLY -> listOf("Wow, you played perfectly! Congratulations!! 🏆🌸", "Well played, you are absolutely awesome!", "Thank you for the wonderful match! 🌷").random()
                PersonalityType.CASUAL -> listOf("gg wrp wp!", "Wow massive outplay, clean win!", "gg! definitely got outplayed there lol").random()
                PersonalityType.COMPETITIVE -> listOf("Arrg! Gg wp. My ping was high but you played tight.", "Outstanding tactics. Gg!", "Gg. That last mistake cost me the match.").random()
                PersonalityType.SNEAKY_TYPO -> listOf("gwpwp clean gamee!", "omg gg wp! rank promotion for u", "gg you are super god tier").random()
            }
            ReasonForChat.TIED_GAME -> when (style) {
                PersonalityType.FRIENDLY -> listOf("A beautiful draw! Perfect harmony 🌸", "Nice block battle, a tie!").random()
                PersonalityType.CASUAL -> listOf("phew close run. solid gridlock drawing!", "draw! gg").random()
                PersonalityType.COMPETITIVE -> listOf("Draw... could have pushed but you played robust.", "gg, a deadlock match.").random()
                PersonalityType.SNEAKY_TYPO -> listOf("wow clsee draw!", "draw gg! my hand slips a lot").random()
            }
            ReasonForChat.WON_GAME -> "gg!" // Highly unlikely as AI tries 100% to lose
        }

        val msg = ChatMessage(sender = _opponent.value.name, text = text, isPlayer = false)
        _chatMessages.value = _chatMessages.value + msg
    }

    private fun getOpponentReplyToPlayerPreset(preset: String, personality: PersonalityType): String {
        return when (preset) {
            "GLHF! 👋" -> when (personality) {
                PersonalityType.FRIENDLY -> "You too! Have loads of fun! 🎉"
                PersonalityType.CASUAL -> "thx m8, let's go! 🥤"
                PersonalityType.COMPETITIVE -> "Haha thanks. May the points keep piling up."
                PersonalityType.SNEAKY_TYPO -> "glhf m8 ty!"
            }
            "Nice move! 🎯" -> when (personality) {
                PersonalityType.FRIENDLY -> "Thank you so much! You are so sweet! 🥰"
                PersonalityType.CASUAL -> "haha thanks, calculated 100%"
                PersonalityType.COMPETITIVE -> "Appreciated. I had to calculate that line risk."
                PersonalityType.SNEAKY_TYPO -> "thx I tried my bset lol"
            }
            "Oops... 💀" -> when (personality) {
                PersonalityType.FRIENDLY -> "Oh no! Don't worry, mistakes happen! 🌸"
                PersonalityType.CASUAL -> "rip! absolute oof moments"
                PersonalityType.COMPETITIVE -> "Ah, clicked wrong? Take your time next turn!"
                PersonalityType.SNEAKY_TYPO -> "haha rip, my thumb is slip too"
            }
            "No way! 🙀" -> when (personality) {
                PersonalityType.FRIENDLY -> "Yes way! This is super exciting! 🎉"
                PersonalityType.CASUAL -> "haha wild times indeed"
                PersonalityType.COMPETITIVE -> "Surprised? The grid tactics are intense here."
                PersonalityType.SNEAKY_TYPO -> "ikr!! crazy gamee"
            }
            "GG WP! 🏆" -> when (personality) {
                PersonalityType.FRIENDLY -> "GG WP! You made my day! Thank you! 💕"
                PersonalityType.CASUAL -> "gg indeed wpwp!"
                PersonalityType.COMPETITIVE -> "gg wp, clean sequence."
                PersonalityType.SNEAKY_TYPO -> "ggwp thx for the gam!"
            }
            else -> "gg!"
        }
    }

    fun playAgain() {
        startMatchmaking()
    }

    fun exitToHome() {
        _currentScreen.value = ScreenState.HOME
    }

    fun clearLogHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun grantRewardPoints(points: Int) {
        viewModelScope.launch {
            val currentProfile = playerProfile.value ?: return@launch
            val updatedProfile = currentProfile.copy(
                rankPoints = currentProfile.rankPoints + points
            )
            repository.saveProfile(updatedProfile)
        }
    }
}

// --- FACTORY FOR INJECTING ROOM DATABASE ---

class TicTacToeViewModelFactory(private val repository: GameRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TicTacToeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TicTacToeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- MAIN ACTIVITY AND INTERACTIVES ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdManager.init(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val database = remember { GameDatabase.getDatabase(context) }
                val repository = remember { GameRepository(database.gameDao()) }
                val viewModel: TicTacToeViewModel = viewModel(
                    factory = TicTacToeViewModelFactory(repository)
                )

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0E0D16) // Cosmic Midnight base color
                ) {
                    AppScreenContainer(viewModel)
                }
            }
        }
    }
}

// --- UNITY ADS AD MANAGER AND UI COMPONENT ---

object AdManager {
    // User's actual Unity Ads Game ID
    var GAME_ID = "6119316"
    var INTERSTITIAL_ID = "Interstitial_Android"
    var REWARDED_ID = "Rewarded_Android"
    var BANNER_ID = "Banner_Android"

    var isInitialized = false
    var isInterstitialLoading = false
    var isRewardedLoading = false

    private val loadedPlacements = java.util.Collections.synchronizedSet(HashSet<String>())

    // Callbacks waiting for ads to load successfully or fail
    private val interstitialCallbacks = java.util.Collections.synchronizedList(ArrayList<(Boolean, String?) -> Unit>())
    private val rewardedCallbacks = java.util.Collections.synchronizedList(ArrayList<(Boolean, String?) -> Unit>())

    fun init(context: android.content.Context) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        Log.d("AdManager", "Initializing Unity Ads with Game ID: $GAME_ID (Test Mode: false)")
        
        // testMode is set to false as requested by user to load real live production ads.
        UnityAds.initialize(context, GAME_ID, false, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                mainHandler.post {
                    Log.d("AdManager", "Unity Ads Initialization Complete!")
                    isInitialized = true
                    loadInterstitial(context)
                    loadRewarded(context)
                }
            }

            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError, message: String) {
                mainHandler.post {
                    Log.e("AdManager", "Unity Ads Initialization Failed: [$error] $message")
                    isInitialized = false
                }
            }
        })
    }

    fun loadAppOpen(context: android.content.Context, useFallback: Boolean = false) {
        // App open ads are not supported natively in Unity Ads
    }

    fun showAppOpen(activity: android.app.Activity, onDismiss: () -> Unit) {
        onDismiss()
    }

    fun loadInterstitial(context: android.content.Context, useFallback: Boolean = false) {
        if (isInterstitialLoading) return
        isInterstitialLoading = true
        Log.d("AdManager", "Loading Unity Interstitial Ad: $INTERSTITIAL_ID")
        
        UnityAds.load(INTERSTITIAL_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Log.d("AdManager", "Unity Interstitial Ad Loaded: $placementId")
                    loadedPlacements.add(placementId)
                    isInterstitialLoading = false
                    
                    val callbacks = ArrayList(interstitialCallbacks)
                    interstitialCallbacks.clear()
                    for (cb in callbacks) {
                        cb(true, null)
                    }
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Log.e("AdManager", "Unity Interstitial Ad Failed to Load: $placementId, [$error] $message")
                    loadedPlacements.remove(placementId)
                    isInterstitialLoading = false
                    
                    val callbacks = ArrayList(interstitialCallbacks)
                    interstitialCallbacks.clear()
                    for (cb in callbacks) {
                        cb(false, message)
                    }
                }
            }
        })
    }

    fun showInterstitial(activity: android.app.Activity, onDismiss: () -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            if (loadedPlacements.contains(INTERSTITIAL_ID)) {
                Log.d("AdManager", "Showing Unity Interstitial Ad: $INTERSTITIAL_ID")
                showLiveInterstitial(activity, onDismiss)
            } else {
                Log.w("AdManager", "Unity Interstitial Ad not loaded yet. Showing blocking loading dialog...")
                lateinit var callback: (Boolean, String?) -> Unit
                
                val dialog = showLoadingBlockingDialog(activity, "Loading Sponsor Ad...") {
                    interstitialCallbacks.remove(callback)
                }
                
                callback = { success, errorMessage ->
                    dialog.dismiss()
                    if (success) {
                        showLiveInterstitial(activity, onDismiss)
                    } else {
                        android.widget.Toast.makeText(
                            activity,
                            "Failed to load Sponsor Ad: ${errorMessage ?: "Network Timeout"}. Check network connection & try again.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
                interstitialCallbacks.add(callback)
                loadInterstitial(activity)
            }
        }
    }

    private fun showLiveInterstitial(activity: android.app.Activity, onDismiss: () -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        UnityAds.show(activity, INTERSTITIAL_ID, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                mainHandler.post {
                    Log.e("AdManager", "Unity Interstitial Show Failed: $placementId, [$error] $message")
                    loadedPlacements.remove(placementId)
                    loadInterstitial(activity)
                    android.widget.Toast.makeText(
                        activity,
                        "Sponsor Ad failed to play ($message). Please check your internet connection.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onUnityAdsShowStart(placementId: String) {
                Log.d("AdManager", "Unity Interstitial Show Started: $placementId")
            }

            override fun onUnityAdsShowClick(placementId: String) {
                Log.d("AdManager", "Unity Interstitial Clicked: $placementId")
            }

            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                mainHandler.post {
                    Log.d("AdManager", "Unity Interstitial Show Completed: $placementId, State: $state")
                    loadedPlacements.remove(placementId)
                    loadInterstitial(activity)
                    onDismiss()
                }
            }
        })
    }

    fun loadRewarded(context: android.content.Context, useFallback: Boolean = false) {
        if (isRewardedLoading) return
        isRewardedLoading = true
        Log.d("AdManager", "Loading Unity Rewarded Ad: $REWARDED_ID")
        
        UnityAds.load(REWARDED_ID, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Log.d("AdManager", "Unity Rewarded Ad Loaded: $placementId")
                    loadedPlacements.add(placementId)
                    isRewardedLoading = false
                    
                    val callbacks = ArrayList(rewardedCallbacks)
                    rewardedCallbacks.clear()
                    for (cb in callbacks) {
                        cb(true, null)
                    }
                }
            }

            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Log.e("AdManager", "Unity Rewarded Ad Failed to Load: $placementId, [$error] $message")
                    loadedPlacements.remove(placementId)
                    isRewardedLoading = false
                    
                    val callbacks = ArrayList(rewardedCallbacks)
                    rewardedCallbacks.clear()
                    for (cb in callbacks) {
                        cb(false, message)
                    }
                }
            }
        })
    }

    fun showRewarded(activity: android.app.Activity, onRewardEarned: (Int) -> Unit, onDismiss: () -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            if (loadedPlacements.contains(REWARDED_ID)) {
                Log.d("AdManager", "Showing Unity Rewarded Ad: $REWARDED_ID")
                showLiveRewarded(activity, onRewardEarned, onDismiss)
            } else {
                Log.w("AdManager", "Unity Rewarded Ad not loaded yet. Showing blocking loading dialog...")
                lateinit var callback: (Boolean, String?) -> Unit
                
                val dialog = showLoadingBlockingDialog(activity, "Loading Sponsor Video...") {
                    rewardedCallbacks.remove(callback)
                }
                
                callback = { success, errorMessage ->
                    dialog.dismiss()
                    if (success) {
                        showLiveRewarded(activity, onRewardEarned, onDismiss)
                    } else {
                        android.widget.Toast.makeText(
                            activity,
                            "Failed to load Sponsor Video: ${errorMessage ?: "Network Timeout"}. Check network connection & try again.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
                rewardedCallbacks.add(callback)
                loadRewarded(activity)
            }
        }
    }

    private fun showLiveRewarded(activity: android.app.Activity, onRewardEarned: (Int) -> Unit, onDismiss: () -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        UnityAds.show(activity, REWARDED_ID, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                mainHandler.post {
                    Log.e("AdManager", "Unity Rewarded Show Failed: $placementId, [$error] $message")
                    loadedPlacements.remove(placementId)
                    loadRewarded(activity)
                    android.widget.Toast.makeText(
                        activity,
                        "Sponsor Video failed to play ($message). Please check your internet connection.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onUnityAdsShowStart(placementId: String) {
                Log.d("AdManager", "Unity Rewarded Show Started: $placementId")
            }

            override fun onUnityAdsShowClick(placementId: String) {
                Log.d("AdManager", "Unity Rewarded Clicked: $placementId")
            }

            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                mainHandler.post {
                    Log.d("AdManager", "Unity Rewarded Completed: $placementId, State: $state")
                    loadedPlacements.remove(placementId)
                    loadRewarded(activity)
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        onRewardEarned(1)
                        onDismiss()
                    } else {
                        android.widget.Toast.makeText(
                            activity,
                            "You must watch the full sponsor ad to play.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        })
    }
}

fun showLoadingBlockingDialog(
    activity: android.app.Activity,
    message: String,
    onCancel: () -> Unit
): android.app.Dialog {
    val dialog = android.app.Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    val composeView = androidx.compose.ui.platform.ComposeView(activity).apply {
        // Set ViewTree owners via reflection to avoid compile classpath resolution quirks
        try {
            val lifecycleClazz = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val setLifecycleMethod = lifecycleClazz.getMethod(
                "set",
                android.view.View::class.java,
                androidx.lifecycle.LifecycleOwner::class.java
            )
            setLifecycleMethod.invoke(null, this, activity)
        } catch (e: Exception) {
            Log.e("AdManager", "Could not set ViewTreeLifecycleOwner: ${e.message}")
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
            Log.e("AdManager", "Could not set ViewTreeViewModelStoreOwner: ${e.message}")
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
            Log.e("AdManager", "Could not set ViewTreeSavedStateRegistryOwner: ${e.message}")
        }

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0E17)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF00FF87),
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 5.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "PLEASE WAIT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5A4C0),
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sponsor Advertisement is loading...\nGame won't start until ad plays.",
                        fontSize = 11.sp,
                        color = Color(0xFF8B8A9D),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    OutlinedButton(
                        onClick = {
                            dialog.dismiss()
                            onCancel()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.radialGradient(listOf(Color(0xFF423F5E), Color(0xFF423F5E)))
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Cancel Match",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
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
    var currentBannerId by remember { mutableStateOf(AdManager.BANNER_ID) }

    androidx.compose.runtime.key(currentBannerId) {
        androidx.compose.ui.viewinterop.AndroidView<android.view.View>(
            modifier = modifier.fillMaxWidth(),
            factory = { context ->
                val activity = context.findActivity()
                if (activity != null) {
                    val banner = BannerView(
                        activity,
                        currentBannerId,
                        UnityBannerSize(320, 50)
                    )
                    banner.listener = object : BannerView.IListener {
                        override fun onBannerLoaded(bannerView: BannerView?) {
                            Log.d("UnityBanner", "Banner loaded successfully: $currentBannerId")
                        }

                        override fun onBannerClick(bannerView: BannerView?) {
                            Log.d("UnityBanner", "Banner clicked")
                        }

                        override fun onBannerFailedToLoad(bannerView: BannerView?, errorInfo: BannerErrorInfo?) {
                            Log.e("UnityBanner", "Banner failed to load: ${errorInfo?.errorMessage}")
                        }

                        override fun onBannerLeftApplication(bannerView: BannerView?) {
                            Log.d("UnityBanner", "Banner left application")
                        }

                        override fun onBannerShown(bannerView: BannerView?) {
                            Log.d("UnityBanner", "Banner shown")
                        }
                    }
                    banner.load()
                    banner as android.view.View
                } else {
                    android.view.View(context)
                }
            },
            update = {
                // Key-based recreation handles updates
            }
        )
    }
}

// --- SYSTEM RANK DEFINITIONS & PROGRESS CALCULATORS ---

fun getRankBadgeInfo(points: Int): Pair<String, Color> {
    return when {
        points < 300 -> "Bronze IV" to Color(0xFFCD7F32)
        points < 500 -> "Bronze I" to Color(0xFFCD7F32)
        points < 700 -> "Silver IV" to Color(0xFFC0C0C0)
        points < 900 -> "Silver I" to Color(0xFFC0C0C0)
        points < 1100 -> "Gold IV" to Color(0xFFFFD700)
        points < 1300 -> "Gold II" to Color(0xFFFFD700)
        points < 1500 -> "Platinum III" to Color(0xFFE5E4E2)
        points < 1800 -> "Platinum I" to Color(0xFFE5E4E2)
        else -> "Diamond Elite" to Color(0xFF00FFFF)
    }
}

@Composable
fun AppScreenContainer(viewModel: TicTacToeViewModel) {
    val screenState by viewModel.currentScreen.collectAsState()

    AnimatedContent(
        targetState = screenState,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "ScreenTransition"
    ) { targetScreen ->
        when (targetScreen) {
            ScreenState.ONBOARDING -> ProfileSetupScreen(viewModel)
            ScreenState.HOME -> HomeDashboardScreen(viewModel)
            ScreenState.MATCHMAKING -> MatchmakingScreen(viewModel)
            ScreenState.MATCH_FOUND -> MatchFoundCards(viewModel)
            ScreenState.GAMEPLAY -> GameplayScreen(viewModel)
            ScreenState.GAMEOVER -> GameOverScreen(viewModel)
        }
    }
}

// --- 1. PROFILE SETUP / NICKNAME ONBOARDING ---

@Composable
fun ProfileSetupScreen(viewModel: TicTacToeViewModel) {
    var textInput by remember { mutableStateOf("") }
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
        // App Logo / Symbol
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF00FF87), Color.Transparent)), CircleShape)
                .wrapContentSize(Alignment.Center)
        ) {
            Text(
                text = "⚔️",
                fontSize = 42.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Tic Tac Toe",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.sp
        )

        Text(
            text = "CHAMPIONS ARENA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00FF87),
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

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
                    text = "Player Profile Name",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Text(
                    text = "Set your nickname to join the online ranked matchmaking ladder.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF8A84AC),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { if (it.length <= 16) textInput = it },
                    placeholder = { Text("E.g., SkyWalker_99", color = Color(0xFF5A547C)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF2C274E),
                        focusedContainerColor = Color(0xFF110E1E),
                        unfocusedContainerColor = Color(0xFF110E1E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nickname_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.submitNickname(textInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("submit_nickname_button")
                ) {
                    Text(
                        text = "REGISTER & ENTER ARENA",
                        color = Color(0xFF040306),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// --- 2. HOME DASHBOARD ---

@Composable
fun HomeDashboardScreen(viewModel: TicTacToeViewModel) {
    val profile by viewModel.playerProfile.collectAsState()
    val history by viewModel.matchHistory.collectAsState()
    val context = LocalContext.current

    var showInsufficientFundsDetails by remember { mutableStateOf(false) }
    var showAdPromptDetails by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    if (showInsufficientFundsDetails) {
        AlertDialog(
            onDismissRequest = { showInsufficientFundsDetails = false },
            containerColor = Color(0xFF161524),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF8B8A9D),
            title = {
                Text(
                    text = "⚠️ INSUFFICIENT BALANCE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFFF5252)
                )
            },
            text = {
                Column {
                    Text(
                        text = "To join the Competitive Arena, you need at least ₹0.10 entry fee.",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Don't worry! Watch a short sponsor ad video now to receive a FREE +₹0.50 Top-Up instantly and keep playing!",
                        fontSize = 12.sp,
                        color = Color(0xFF8B8A9D)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            showInsufficientFundsDetails = false
                            statusMessage = "Loading Sponsor Video..."
                            viewModel.watchRewardedAdForTopUp(
                                activity = activity,
                                onSuccess = {
                                    statusMessage = "Success! Received Free ₹0.50 Top-Up!"
                                },
                                onFailure = {
                                    statusMessage = "Ad closed or failed. Try again!"
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "WATCH FREE AD", color = Color(0xFF040306), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsufficientFundsDetails = false }) {
                    Text(text = "CANCEL", color = Color(0xFF8B8A9D))
                }
            }
        )
    }

    if (showAdPromptDetails) {
        AlertDialog(
            onDismissRequest = { showAdPromptDetails = false },
            containerColor = Color(0xFF161524),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF8B8A9D),
            title = {
                Text(
                    text = "⚔️ JOIN ARENA QUEUE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF00FF87)
                )
            },
            text = {
                Column {
                    Text(
                        text = "To play a match and climb the leaderboard divisions, you must pay the entry fee and support our sponsors.",
                        fontSize = 13.sp,
                        color = Color(0xFF8B8A9D)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F0D1A), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ENTRY FEE", fontSize = 10.sp, color = Color(0xFF8B8A9D), fontWeight = FontWeight.Bold)
                            Text("₹0.10", fontSize = 15.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("SPONSOR AD", fontSize = 10.sp, color = Color(0xFF8B8A9D), fontWeight = FontWeight.Bold)
                            Text("1 Video Clip", fontSize = 15.sp, color = Color(0xFF7000FF), fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("WIN PAYOUT", fontSize = 10.sp, color = Color(0xFF8B8A9D), fontWeight = FontWeight.Bold)
                            Text("+₹0.18", fontSize = 15.sp, color = Color(0xFF00FF87), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Loss of this match will forfeit the ₹0.10 entry fee. Draws refund your entry fee fully (₹0.10)!",
                        fontSize = 11.sp,
                        color = Color(0xFF8B8A9D)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            showAdPromptDetails = false
                            statusMessage = "Loading Sponsor Ad..."
                            viewModel.checkAndStartMatch(
                                activity = activity,
                                onSuccess = {
                                    statusMessage = "Paid ₹0.10 and authenticated! Finding match..."
                                    viewModel.startMatchmaking()
                                },
                                onFail = { reason ->
                                    if (reason == "INSUFFICIENT_FUNDS") {
                                        showInsufficientFundsDetails = true
                                    } else {
                                        statusMessage = "You must watch the ad completely to enter."
                                    }
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7000FF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "PAY & WATCH AD", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdPromptDetails = false }) {
                    Text(text = "CANCEL", color = Color(0xFF8B8A9D))
                }
            }
        )
    }

    if (statusMessage != null) {
        LaunchedEffect(statusMessage) {
            delay(3500)
            statusMessage = null
        }
        AlertDialog(
            onDismissRequest = { statusMessage = null },
            containerColor = Color(0xFF161524),
            title = { Text("Arena Alert", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(statusMessage ?: "", color = Color.White, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { statusMessage = null }) {
                    Text("OK", color = Color(0xFF00FF87))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0D16))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper Title Header
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome Back,",
                    fontSize = 14.sp,
                    color = Color(0xFF8B8A9D)
                )
                Text(
                    text = profile?.username ?: "Loading...",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Wallet balance display pill
            val balance = profile?.walletBalance ?: 10.00
            Box(
                modifier = Modifier
                    .background(Color(0xFF161524), RoundedCornerShape(20.dp))
                    .border(1.5.dp, Color(0xFF00FF87), RoundedCornerShape(20.dp))
                    .clickable {
                        val activity = context.findActivity()
                        if (activity != null) {
                            statusMessage = "Loading Top-Up Video..."
                            viewModel.watchRewardedAdForTopUp(
                                activity = activity,
                                onSuccess = {
                                    statusMessage = "Success! Received Free ₹0.50 Top-Up!"
                                },
                                onFailure = {
                                    statusMessage = "Top-Up Ad closed or failed."
                                }
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color(0xFF00FF87)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = String.format(Locale.US, "%.2f", balance),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Professional Rating Card
        val rankInfo = getRankBadgeInfo(profile?.rankPoints ?: 1200)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161524)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Brush.radialGradient(listOf(rankInfo.second, Color(0xFF23213E))), RoundedCornerShape(24.dp))
                .shadow(12.dp, RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LADDER RANK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = rankInfo.second,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = rankInfo.first,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF23213C), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${profile?.rankPoints ?: 1200} LP",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Wins", fontSize = 11.sp, color = Color(0xFF8B8A9D))
                        Text(text = "${profile?.wins ?: 0}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF87))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Draws", fontSize = 11.sp, color = Color(0xFF8B8A9D))
                        Text(text = "${profile?.draws ?: 0}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B8A9D))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Losses", fontSize = 11.sp, color = Color(0xFF8B8A9D))
                        Text(text = "${profile?.losses ?: 0}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val total = (profile?.wins ?: 0) + (profile?.losses ?: 0) + (profile?.draws ?: 0)
                        val rate = if (total == 0) "0%" else "${((profile?.wins ?: 0) * 100) / total}%"
                        Text(text = "Win Rate", fontSize = 11.sp, color = Color(0xFF8B8A9D))
                        Text(text = rate, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Play Button
        Button(
            onClick = {
                val currentBal = profile?.walletBalance ?: 10.00
                if (currentBal < 0.10) {
                    showInsufficientFundsDetails = true
                } else {
                    showAdPromptDetails = true
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7000FF)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(12.dp, RoundedCornerShape(16.dp))
                .testTag("play_button")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "FIND COMPETITIVE MATCH",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- REWARDED AD AND INTERSTITIAL AD SECTION ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141322)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1F1E33), RoundedCornerShape(16.dp)),
        ) {
            val context = LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "💎 PREMIUM LADDER REWARD",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7000FF),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Claim +25 LP Points Boost",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Watch a fast sponsor video clip.",
                        fontSize = 11.sp,
                        color = Color(0xFF8B8A9D)
                    )
                }

                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            AdManager.showRewarded(
                                activity = activity,
                                onRewardEarned = { amount ->
                                    viewModel.grantRewardPoints(25)
                                },
                                onDismiss = {
                                    AdManager.loadRewarded(activity)
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF0E0D16),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CLAIM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF0E0D16)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Match History list
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT MATCHES",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B8A9D),
                letterSpacing = 1.sp
            )

            if (history.isNotEmpty()) {
                Text(
                    text = "Clear Logs",
                    fontSize = 12.sp,
                    color = Color(0xFFFF5252),
                    modifier = Modifier.clickable { viewModel.clearLogHistory() }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF141322), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📭", fontSize = 38.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No games played on this ladder yet.",
                        color = Color(0xFF5E5C70),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { record ->
                    MatchHistoryRow(record)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        AdmobBanner(modifier = Modifier.padding(vertical = 4.dp))
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun MatchHistoryRow(record: MatchRecord) {
    val isWin = record.result == "WIN"
    val isDraw = record.result == "DRAW"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141322), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF1F1E33), RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Circle with Win / Loss symbol
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isWin) Color(0xFF00FF87).copy(alpha = 0.15f)
                        else if (isDraw) Color(0xFF8B8A9D).copy(alpha = 0.15f)
                        else Color(0xFFFF5252).copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isWin) "W" else if (isDraw) "D" else "L",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (isWin) Color(0xFF00FF87) else if (isDraw) Color(0xFF8B8A9D) else Color(0xFFFF5252)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = record.opponentName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Opponent: ${record.opponentRank}",
                    color = Color(0xFF5C5A75),
                    fontSize = 11.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (record.lpChange >= 0) "+${record.lpChange} LP" else "${record.lpChange} LP",
                color = if (record.lpChange >= 0) Color(0xFF00FF87) else Color(0xFFFF5252),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            val dateStr = remember(record.timestamp) {
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(record.timestamp))
            }
            Text(
                text = dateStr,
                color = Color(0xFF5C5A75),
                fontSize = 10.sp
            )
        }
    }
}

// --- 3. MATCHMAKING SCREEN ---

@Composable
fun MatchmakingScreen(viewModel: TicTacToeViewModel) {
    val duration by viewModel.matchmakingTimeSec.collectAsState()

    // Infinite orbit scale animation
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

    val scaleAmt by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
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
            text = "SEARCHING OPPONENT",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF7000FF),
            letterSpacing = 3.sp
        )

        Text(
            text = "LADDER MATCH",
            fontSize = 11.sp,
            color = Color(0xFF5E5C75),
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(44.dp))

        // Radar Design
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(scaleAmt)
                .border(1.5.dp, Color(0xFF1E1A2E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Layer 1
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .border(1.dp, Color(0xFF2C2646), CircleShape)
            )

            // Dynamic Sweeper Arc rotating
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .rotate(sweepRotation),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF7000FF), CircleShape)
                )
            }

            // Central icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF1A152B), CircleShape)
                    .border(1.5.dp, Color(0xFF7000FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚡",
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(44.dp))

        Text(
            text = "Elapse Time: ${duration}s",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Analyzing lobby pool for stable connection...",
            color = Color(0xFF8B8A9D),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(30.dp))

        LinearProgressIndicator(
            color = Color(0xFF7000FF),
            trackColor = Color(0xFF1B182B),
            modifier = Modifier
                .width(160.dp)
                .clip(CircleShape)
        )
    }
}

// --- 4. MATCH FOUND TRANSITION SCREEN ---

@Composable
fun MatchFoundCards(viewModel: TicTacToeViewModel) {
    val playerProfile by viewModel.playerProfile.collectAsState()
    val opponent by viewModel.opponent.collectAsState()

    // Slide reveals
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
            text = "MATCH FOUND!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF00FF87),
            letterSpacing = 2.sp
        )

        Text(
            text = "SECURE SERVER CONNECTED",
            fontSize = 11.sp,
            color = Color(0xFF5E5C75),
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(50.dp))

        // VS Card Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player Card
            AnimatedVisibility(
                visible = displayed,
                enter = slideInHorizontally(animationSpec = tween(500)) { -it } + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(Brush.linearGradient(listOf(Color(0xFF7000FF), Color(0xFF4A00E0))), CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "👤", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = playerProfile?.username ?: "You",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.widthIn(max = 100.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Rating: ${playerProfile?.rankPoints} LP",
                        color = Color(0xFF8B8A9D),
                        fontSize = 11.sp
                    )
                }
            }

            // VS Logo
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFFFF5252), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "VS",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            // Opponent Card
            AnimatedVisibility(
                visible = displayed,
                enter = slideInHorizontally(animationSpec = tween(500)) { it } + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(opponent.avatarBg, CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = opponent.avatarEmoji, fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = opponent.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.widthIn(max = 120.dp),
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

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = opponent.tagline,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFFFD700),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// --- 5. COMPREHENSIVE LOBBY GAMEPLAY ---

@Composable
fun GameplayScreen(viewModel: TicTacToeViewModel) {
    val playerProfile by viewModel.playerProfile.collectAsState()
    val opponent by viewModel.opponent.collectAsState()
    val board by viewModel.board.collectAsState()
    val isTurn by viewModel.isPlayerTurn.collectAsState()
    val activeTime by viewModel.turnTimer.collectAsState()
    val isThinking by viewModel.isOpponentThinking.collectAsState()
    val ping by viewModel.pingMs.collectAsState()
    val chats by viewModel.chatMessages.collectAsState()

    val chatListState = rememberLazyListState()

    // Auto scroll chat to bottom when message arrives
    LaunchedEffect(chats.size) {
        if (chats.isNotEmpty()) {
            chatListState.animateScrollToItem(chats.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0A12))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High connection status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141221))
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF00FF87), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Rank Match Lobby",
                    fontSize = 11.sp,
                    color = Color(0xFF8E8BB1)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Server ping: ${ping}ms",
                    fontSize = 11.sp,
                    color = if (ping < 50) Color(0xFF00FF87) else Color(0xFFFFD700)
                )
            }
        }

        // Active Player info & timing indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player Details Left
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .opacityIf(!isTurn && !isThinking)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF7000FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = playerProfile?.username ?: "You",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Sign: X",
                        color = Color(0xFF00FF87),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // VS & countdown block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isTurn) Color(0xFF00FF87).copy(alpha = 0.15f) else Color(0xFFFF5252).copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, if (isTurn) Color(0xFF00FF87) else Color(0xFFFF5252), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${activeTime}s",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = if (isTurn) Color(0xFF00FF87) else Color(0xFFFF5252)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isTurn) "YOUR TURN" else "PLAYING",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8B8A9D),
                    letterSpacing = 1.sp
                )
            }

            // Opponent Details Right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .weight(1f)
                    .opacityIf(isTurn || isThinking)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = opponent.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Sign: O",
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(opponent.avatarBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(opponent.avatarEmoji, fontSize = 18.sp)
                }
            }
        }

        // Opponent is typing overlay if processing
        AnimatedVisibility(visible = isThinking) {
            Row(
                modifier = Modifier
                    .background(Color(0xFF1B192E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DotPulseAnimation()
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Opponent is picking a cell...",
                    color = Color(0xFFBDC2E8),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // THE BOARD GRID
        GameBoardGrid(board = board, isMyTurn = isTurn, onSelectCell = { index ->
            viewModel.makePlayerMove(index)
        })

        Spacer(modifier = Modifier.height(16.dp))

        // INTERACTIVE IN-GAME CHAT BOARD (Realism driver)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13111E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .border(1.dp, Color(0xFF221F35), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Mini chat title banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF191726))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MATCH CHAT LOG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF908CB4),
                        letterSpacing = 1.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFF4C4A63),
                        modifier = Modifier.size(12.dp)
                    )
                }

                if (chats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lobby is ready. Tap prompts below to chat!",
                            color = Color(0xFF46445B),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = chatListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(chats) { item ->
                            ChatBubbleRow(item)
                        }
                    }
                }

                // Presets prompt keyboard
                HorizontalPresetSelector(onSelectedText = { preset ->
                    viewModel.sendPlayerChat(preset)
                })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Opacity modifier helper
fun Modifier.opacityIf(condition: Boolean): Modifier {
    return this.graphicsLayer(alpha = if (condition) 1.0f else 0.45f)
}

// --- COMPOSE RIPPLE GAMEBOARD ---

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
        // Grid lines drawing
        Column(modifier = Modifier.fillMaxSize()) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (col in 0..2) {
                        val index = row * 3 + col
                        val mark = board[index]

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (mark.isEmpty()) Color(0xFF1A182E) else Color(0xFF23203D)
                                )
                                .border(
                                    1.dp,
                                    if (mark == "X") Color(0xFF00FF87).copy(alpha = 0.5f)
                                    else if (mark == "O") Color(0xFFFF5252).copy(alpha = 0.5f)
                                    else Color(0xFF2B2844),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(
                                    enabled = mark.isEmpty() && isMyTurn,
                                    onClick = { onSelectCell(index) }
                                )
                                .testTag("cell_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = mark,
                                transitionSpec = {
                                    scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith fadeOut()
                                },
                                label = "MarkReveal"
                            ) { symbol ->
                                when (symbol) {
                                    "X" -> Text(
                                        text = "X",
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF00FF87)
                                    )
                                    "O" -> Text(
                                        text = "O",
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFF5252)
                                    )
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

// --- CHAT COMPONENTS ---

@Composable
fun ChatBubbleRow(msg: ChatMessage) {
    val isUser = msg.isPlayer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Text(
                text = msg.sender,
                fontSize = 10.sp,
                color = Color(0xFF6B6984),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )

            Box(
                modifier = Modifier
                    .background(
                        if (isUser) Color(0xFF7000FF) else Color(0xFF1F1E33),
                        RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isUser) 12.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 12.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .widthIn(max = 240.dp)
            ) {
                Text(
                    text = msg.text,
                    color = Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun HorizontalPresetSelector(onSelectedText: (String) -> Unit) {
    val list = listOf("GLHF! 👋", "Nice move! 🎯", "Oops... 💀", "No way! 🙀", "GG WP! 🏆")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF110F1D))
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = "TAP PRESET PROMPT TO CHAT:",
            fontSize = 9.sp,
            color = Color(0xFF53516B),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            list.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1E1C30), RoundedCornerShape(10.dp))
                        .clickable { onSelectedText(prompt) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("chat_preset_${prompt.take(4)}")
                ) {
                    Text(
                        text = prompt,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}



// --- DOTS PULSING ANIMATION ELEMENT ---

@Composable
fun DotPulseAnimation() {
    val transition = rememberInfiniteTransition(label = "Dots")
    val dotCount = 3
    val dots = (0 until dotCount).map { index ->
        transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = index * 150, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Dot $index"
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dots.forEach { dotScale ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer(alpha = dotScale.value)
                    .background(Color(0xFF00FF87), CircleShape)
            )
        }
    }
}

// --- 6. GAME OVER DIVISION / PROMOTION RATINGS SUMMARY ---

@Composable
fun GameOverScreen(viewModel: TicTacToeViewModel) {
    val profile by viewModel.playerProfile.collectAsState()
    val opponent by viewModel.opponent.collectAsState()
    val winnerSymbol by viewModel.winner.collectAsState()

    val isWin = winnerSymbol == "X"
    val isDraw = winnerSymbol == "DRAW"

    val context = LocalContext.current

    var showInsufficientFundsDetails by remember { mutableStateOf(false) }
    var showAdPromptDetails by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    if (showInsufficientFundsDetails) {
        AlertDialog(
            onDismissRequest = { showInsufficientFundsDetails = false },
            containerColor = Color(0xFF161524),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF8B8A9D),
            title = {
                Text(
                    text = "⚠️ INSUFFICIENT BALANCE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFFFF5252)
                )
            },
            text = {
                Column {
                    Text(
                        text = "To join the Competitive Arena, you need at least ₹0.10 entry fee.",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Don't worry! Watch a short sponsor ad video now to receive a FREE +₹0.50 Top-Up instantly and keep playing!",
                        fontSize = 12.sp,
                        color = Color(0xFF8B8A9D)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            showInsufficientFundsDetails = false
                            statusMessage = "Loading Sponsor Video..."
                            viewModel.watchRewardedAdForTopUp(
                                activity = activity,
                                onSuccess = {
                                    statusMessage = "Success! Received Free ₹0.50 Top-Up!"
                                },
                                onFailure = {
                                    statusMessage = "Ad closed or failed. Try again!"
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "WATCH FREE AD", color = Color(0xFF040306), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsufficientFundsDetails = false }) {
                    Text(text = "CANCEL", color = Color(0xFF8B8A9D))
                }
            }
        )
    }

    if (showAdPromptDetails) {
        AlertDialog(
            onDismissRequest = { showAdPromptDetails = false },
            containerColor = Color(0xFF161524),
            titleContentColor = Color.White,
            textContentColor = Color(0xFF8B8A9D),
            title = {
                Text(
                    text = "⚔️ JOIN ARENA QUEUE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF00FF87)
                )
            },
            text = {
                Column {
                    Text(
                        text = "To play a match and climb the leaderboard divisions, you must pay the entry fee and support our sponsors.",
                        fontSize = 13.sp,
                        color = Color(0xFF8B8A9D)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F0D1A), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ENTRY FEE", fontSize = 10.sp, color = Color(0xFF8B8A9D), fontWeight = FontWeight.Bold)
                            Text("₹0.10", fontSize = 15.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("SPONSOR AD", fontSize = 10.sp, color = Color(0xFF8B8A9D), fontWeight = FontWeight.Bold)
                            Text("1 Video Clip", fontSize = 15.sp, color = Color(0xFF7000FF), fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("WIN PAYOUT", fontSize = 10.sp, color = Color(0xFF8B8A9D), fontWeight = FontWeight.Bold)
                            Text("+₹0.18", fontSize = 15.sp, color = Color(0xFF00FF87), fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Loss of this match will forfeit the ₹0.10 entry fee. Draws refund your entry fee fully (₹0.10)!",
                        fontSize = 11.sp,
                        color = Color(0xFF8B8A9D)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            showAdPromptDetails = false
                            statusMessage = "Loading Sponsor Ad..."
                            viewModel.checkAndStartMatch(
                                activity = activity,
                                onSuccess = {
                                    statusMessage = "Paid ₹0.10 and authenticated! Finding match..."
                                    viewModel.startMatchmaking()
                                },
                                onFail = { reason ->
                                    if (reason == "INSUFFICIENT_FUNDS") {
                                        showInsufficientFundsDetails = true
                                    } else {
                                        statusMessage = "You must watch the ad completely to enter."
                                    }
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7000FF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "PAY & WATCH AD", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdPromptDetails = false }) {
                    Text(text = "CANCEL", color = Color(0xFF8B8A9D))
                }
            }
        )
    }

    if (statusMessage != null) {
        LaunchedEffect(statusMessage) {
            delay(3500)
            statusMessage = null
        }
        AlertDialog(
            onDismissRequest = { statusMessage = null },
            containerColor = Color(0xFF161524),
            title = { Text("Arena Alert", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(statusMessage ?: "", color = Color.White, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { statusMessage = null }) {
                    Text("OK", color = Color(0xFF00FF87))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0910))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High-fidelity outcome headline
        Text(
            text = if (isWin) "COMPETITIVE VICTORY!" else if (isDraw) "TACTICAL DEADLOCK" else "MATCH OVER",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = if (isWin) Color(0xFF00FF87) else if (isDraw) Color(0xFF8B8A9D) else Color(0xFFFF5252),
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Large Victory Emblem with Opponent details
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151325)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.5.dp,
                    if (isWin) Color(0xFF00FF87).copy(alpha = 0.4f) else Color(0xFF2C274B),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Opponent signature card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(opponent.avatarBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(opponent.avatarEmoji, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = opponent.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Opponent Tier: ${opponent.rankTitle}",
                            color = Color(0xFF8B8A9D),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Rating points meter counting
                Text(
                    text = "LADDER PROGRESSION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8B8A9D),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                val rankInfo = getRankBadgeInfo(profile?.rankPoints ?: 1200)
                Box(
                    modifier = Modifier
                        .background(Color(0xFF221F38), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${profile?.rankPoints ?: 1200} LP  (${rankInfo.first})",
                        color = rankInfo.second,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isWin) "+26 LP (Rank Increase!)" else if (isDraw) "+2 LP (Safe Draw)" else "-12 LP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isWin || isDraw) Color(0xFF00FF87) else Color(0xFFFF5252)
                )
            }
        }

    Spacer(modifier = Modifier.height(36.dp))

    val context = LocalContext.current

    // Play Again & Exit button columns
    Button(
        onClick = {
            val currentBal = profile?.walletBalance ?: 10.00
            if (currentBal < 0.10) {
                showInsufficientFundsDetails = true
            } else {
                showAdPromptDetails = true
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7000FF)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("play_again_button")
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "FIND ANOTHER MATCH",
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = {
            val activity = context.findActivity()
            if (activity != null) {
                AdManager.showInterstitial(activity) {
                    viewModel.exitToHome()
                }
            } else {
                viewModel.exitToHome()
            }
        },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.radialGradient(listOf(Color(0xFF2C274B), Color(0xFF2C274B)))),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("exit_home_button")
    ) {
        Text(
            text = "EXIT TO DASHBOARD",
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
    }
}
