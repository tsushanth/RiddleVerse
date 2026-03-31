
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.5.2/firebase-app.js";
import { getAuth, GoogleAuthProvider, signInWithPopup, signInWithEmailAndPassword, createUserWithEmailAndPassword, signInWithRedirect,  signOut } from "https://www.gstatic.com/firebasejs/10.5.2/firebase-auth.js";

// ✅ Initialize Firebase
const firebaseConfig = {
  apiKey: "AIzaSyCUdOy2BUynLmg3eSJQwRz2sNpIWOSbFq0",
  authDomain: "aipuzzle-3122c.firebaseapp.com",
  projectId: "aipuzzle-3122c",
  storageBucket: "aipuzzle-3122c.firebasestorage.app",
  messagingSenderId: "719851629523",
  appId: "1:719851629523:web:d1f7f9e20e3ef6df129d26",
  measurementId: "G-BJ4LKWX858"
};

const API_BASE_URL = window.location.hostname.includes("localhost")
    ? "http://localhost:8080"
    : "https://puzzleverseai.com"; 

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const provider = new GoogleAuthProvider();

let previousQuestion = "";
const emailAuthContainer = document.getElementById("email-auth-container");
const settingsContainer = document.getElementById("settings-container");
const settingsButton = document.getElementById("settings-button");
const gameContent = document.getElementById("gameContent");
const loginButton = document.getElementById("login-button");
const signOutButton = document.getElementById("sign-out-button");
const resetButton = document.getElementById("reset-progress-button");
const settingsDropdown = document.getElementById("settings-dropdown");
const showLeaderboardButton = document.getElementById("show-leaderboard-button");
const leaderboardModal = document.getElementById("leaderboard-modal");
const closeLeaderboardButton = document.querySelector(".close-button");
const leaderboardList = document.getElementById("leaderboard-list");


// ✅ Handle Settings Dropdown Toggle
settingsButton.addEventListener("click", () => {
    settingsDropdown.style.display = settingsDropdown.style.display === "none" ? "block" : "none";
});

// ✅ Hide Settings Dropdown when clicking outside
document.addEventListener("click", (event) => {
    if (!settingsContainer.contains(event.target)) {
        settingsDropdown.style.display = "none";
    }
});

// ✅ Function to Update UI Based on Auth State
function updateAuthUI(user) {
    if (user) {
        console.log("✅ User is signed in:", user.email);
        loginButton.style.display = "none";
        emailAuthContainer.style.display = "none";
        gameContent.style.display = "block";
        settingsContainer.style.display = "block"; // ✅ Show Sign Out Button
        signOutButton.style.display = "block"; // ✅ Show Sign Out Button
        resetButton.style.display = "block";
    } else {
        console.log("🔴 User is not signed in");
        loginButton.style.display = "block";
        emailAuthContainer.style.display = "block";
        gameContent.style.display = "none";
        settingsContainer.style.display = "none"; // ✅ Hide Sign Out Button
        signOutButton.style.display = "none"; // ✅ Show Sign Out Button
        resetButton.style.display = "none";
    }
}

function updateScoreDisplay() {
    document.getElementById("userScore").textContent = localStorage.getItem("userScore") || 0;
    document.getElementById("deepSeekScore").textContent = localStorage.getItem("deepSeekScore") || 0;
}

// ✅ Handle Sign Up with Email/Password
async function signUpWithEmail() {
    const email = document.getElementById("email-input").value;
    const password = document.getElementById("password-input").value;

    if (!email || !password) {
        alert("Please enter email and password");
        return;
    }

    try {
        const userCredential = await createUserWithEmailAndPassword(auth, email, password);
        const user = userCredential.user;
        console.log("✅ User signed up:", user.email);
        updateAuthUI(user);
    } catch (error) {
        console.error("❌ Sign Up Error:", error.message);
        alert(error.message);
    }
}

// ✅ Handle Sign In with Email/Password
async function signInWithEmail() {
    const email = document.getElementById("email-input").value;
    const password = document.getElementById("password-input").value;

    if (!email || !password) {
        alert("Please enter email and password");
        return;
    }

    try {
        const userCredential = await signInWithEmailAndPassword(auth, email, password);
        const user = userCredential.user;
        console.log("✅ User signed in:", user.email);
        updateAuthUI(user);
    } catch (error) {
        console.error("❌ Sign In Error:", error.message);
        alert(error.message);
    }
}

async function handleSignOut() {
    try {
        await signOut(auth);
        console.log("✅ User signed out successfully");

        // ✅ Remove stored authentication tokens
        localStorage.removeItem("token");
        localStorage.removeItem("email");
        localStorage.removeItem("userId");
        previousQuestion = "";

        updateAuthUI(null);
    } catch (error) {
        console.error("❌ Sign-out error:", error);
    }
}

// ✅ Attach Sign Out to Button
document.addEventListener("DOMContentLoaded", () => {
    const signOutButton = document.getElementById("sign-out-button");
    if (signOutButton) {
        signOutButton.addEventListener("click", handleSignOut);
    }
});

async function resetPuzzleProgress() {
    const token = localStorage.getItem("token");
    const userId = localStorage.getItem("userId"); // Ensure the user ID is stored in localStorage

    if (!userId) {
        console.error("❌ User ID not found in localStorage.");
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/reset-puzzle-progress/${userId}`, {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${token}`,
                "Content-Type": "application/json",
            }
        });

        const data = await response.json();
        // reset user score as well.
        localStorage.setItem("userScore", 0);
        localStorage.setItem("deepSeekScore", 0)
        if (data.success) {
            alert("✅ Puzzle progress has been reset successfully!");
            console.log("✅ Puzzle progress reset.");
        } else {
            alert("❌ Failed to reset puzzle progress.");
            console.error("❌ Reset failed:", data.message);
        }
    } catch (error) {
        console.error("❌ Error resetting puzzle progress:", error);
        alert("❌ Error resetting puzzle progress.");
    }
}

// ✅ Fetch and Display Leaderboard
async function fetchLeaderboard() {
    try {
        const response = await fetch(`${API_BASE_URL}/leaderboard`, {
            method: "GET",
            headers: { "Content-Type": "application/json" },
        });

        const data = await response.json();
        if (!data.success) throw new Error(data.message);

        leaderboardList.innerHTML = ""; // ✅ Clear old leaderboard
        data.leaderboard.forEach((entry, index) => {
            const listItem = document.createElement("li");
            listItem.textContent = `${index + 1}. ${entry.name}: ${entry.score} points`;
            leaderboardList.appendChild(listItem);
        });

        leaderboardModal.style.display = "block"; // ✅ Show leaderboard modal
    } catch (error) {
        console.error("❌ Error fetching leaderboard:", error);
        alert("❌ Failed to load leaderboard.");
    }
}

// ✅ Event Listener for Leaderboard Button
showLeaderboardButton.addEventListener("click", fetchLeaderboard);

// ✅ Close Leaderboard Modal
closeLeaderboardButton.addEventListener("click", () => {
    leaderboardModal.style.display = "none";
});

// ✅ Close modal if user clicks outside
window.addEventListener("click", (event) => {
    if (event.target === leaderboardModal) {
        leaderboardModal.style.display = "none";
    }
});

// ✅ Attach Reset Button Event Listener
document.addEventListener("DOMContentLoaded", () => {
    const resetButton = document.getElementById("reset-progress-button");
    if (resetButton) {
        resetButton.addEventListener("click", resetPuzzleProgress);
    }
});

// ✅ Initialize scores when the page loads
document.addEventListener("DOMContentLoaded", updateScoreDisplay);

document.addEventListener("DOMContentLoaded", () => {
    let isSigningIn = false; // Prevents multiple sign-in attempts

async function signInWithGoogle() {
    if (isSigningIn) {
        console.warn("⚠️ Google Sign-In already in progress...");
        return;
    }

    isSigningIn = true;
    
    try {
        const result = await signInWithPopup(auth, provider);
        const idToken = await result.user.getIdToken();
        
        console.log("✅ User signed in:", result.user);

        // ✅ Send ID Token to Backend
        const response = await fetch(`${API_BASE_URL}/auth/google`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ idToken }),
        });

        const data = await response.json();
        if (data.success) {
            localStorage.setItem("token", data.token);
            localStorage.setItem("email", data.email);
            localStorage.setItem("userId", data.userId);
            console.log("✅ Auth Token stored!");
        }

        return data;
    } catch (error) {
        console.error("❌ Google Sign-In Error:", error);
    } finally {
        isSigningIn = false; // Reset flag
    }
}


    const token = localStorage.getItem("token");
    const signOutButton = document.getElementById("sign-out-button");
    const resetButton = document.getElementById("reset-progress-button");
    const gameContent = document.getElementById("gameContent");
    const loginButton = document.getElementById("login-button");
    document.getElementById("email-signup-button").addEventListener("click", signUpWithEmail);
    document.getElementById("email-signin-button").addEventListener("click", signInWithEmail);
    if (loginButton) {
        loginButton.addEventListener("click", async () => {
            console.log("Logging in...");
            const result = await signInWithGoogle();
            if (result.success) {
                location.reload(); // Reload to show the puzzle
            } else {
                console.log(result);
            }
        });
    }
    if (!token) {
        console.log("User not authenticated. Redirecting to login...");
        loginButton.style.display = "block";
        emailAuthContainer.style.display = "block";
        gameContent.style.display = "none";
        document.getElementById("puzzleContainer").style.display = "none";
        document.getElementById("login-button").addEventListener("click", async () => {
            console.log("logging in");
            const result = await signInWithGoogle();
            if (result.success) {
                location.reload(); // Reload to show the puzzle
            } else {
                console.log(result);
            }
        });
        return;
    } else {
        console.log("✅ User is signed in");
        settingsContainer.style.display = "block";
        signOutButton.style.display = "block"; // ✅ Show Sign Out Button
        resetButton.style.display = "block";
    }
    auth.onAuthStateChanged(updateAuthUI);

    // ✅ If user is logged in, show the game content and hide the login button
    loginButton.style.display = "none";
    emailAuthContainer.style.display = "none";
    gameContent.style.display = "block";

    const loadPuzzleBtn = document.getElementById("loadPuzzleBtn");
    const checkAnswerBtn = document.getElementById("checkAnswerBtn");

    loadPuzzleBtn.addEventListener("click", fetchNextPuzzle);
    checkAnswerBtn.addEventListener("click", checkAnswer);


    let answerShown = false; // Track if answer was shown
    let hintShown = false; // Track if hint was shown
    let currentAnswer = ""; // Store correct answer
    let currentHint = ""; // Store hint

    let correctAnswerText = document.getElementById("correctAnswer");
    let hintText = document.getElementById("hintText");
    let userAnswerInput = document.getElementById("userAnswer");

    // ✅ Fetch Puzzle from Backend
    async function fetchNextPuzzle() {
        try {
            console.log("setting values");
            answerShown = false; // Reset flag
            hintShown = false; // Reset flag
            userAnswerInput.value = "";
            correctAnswerText.style.display = "none";
            hintText.style.display = "none";
            document.getElementById("puzzleContainer").style.display = "none";

            const token = localStorage.getItem("token");
            const puzzleType = document.getElementById("puzzleType").value;

            console.log(`${API_BASE_URL}/fetch-next-puzzle/${puzzleType}`);
            console.log(token);
            const response = await fetch(`${API_BASE_URL}/fetch-next-puzzle/${puzzleType}`, {
                method: "GET",
                headers: { Authorization: `Bearer ${token}` },
            });

            console.log(JSON.stringify(response));
            const data = await response.json();
            if (!data.success) throw new Error(data.message);

            displayPuzzle(data.puzzleData);
        } catch (error) {
            console.error("Error fetching puzzle:", error);
            displayPuzzle({
                question: "No puzzles available",
                answer: "",
                hint: ""
            });
        }
    }

    function displayPuzzle(puzzleData) {
        const puzzleContainer = document.getElementById("puzzleContainer");
    
        if (!puzzleContainer) {
            console.error("❌ Error: puzzleContainer not found in the DOM.");
            return;
        }
    
        puzzleContainer.innerHTML = `<p>${puzzleData.question}</p>`;
        puzzleContainer.style.display = "block"; // ✅ Make it visible
        document.getElementById("answerSection").style.display = "block";
    
        currentAnswer = puzzleData.answer || "No answer available";
        currentHint = puzzleData.hint || "No hint available";
    
        document.getElementById("userAnswer").dataset.correctAnswer = currentAnswer;
    }
    
    // ✅ Show answer functionality
    document.getElementById("showAnswerBtn").addEventListener("click", function () {
        if (!currentAnswer || currentAnswer === "No answer available") {
            alert("❌ No answer available for this puzzle.");
            return;
        }
        correctAnswerText.innerText = `Correct Answer: ${currentAnswer}`;
        correctAnswerText.style.display = "block";
        answerShown = true; // ✅ Mark answer as shown
    });
    
    // ✅ Show hint functionality
    document.getElementById("showHintBtn").addEventListener("click", function () {
        if (!currentHint || currentHint === "No hint available") {
            alert("❌ No hint available for this puzzle.");
            return;
        }
        hintText.innerText = `Hint: ${currentHint}`;
        hintText.style.display = "block";
        hintShown = true; // ✅ Mark hint as shown
    });
    
    // ✅ Modified checkAnswer function to prevent scoring if answer was shown
    async function checkAnswer() {
        const userAnswer = document.getElementById("userAnswer").value.trim();
        const correctAnswer = currentAnswer;
        const puzzleQuestion = document.getElementById("puzzleContainer").textContent;
        const puzzleType = document.getElementById("puzzleType").value;
    
        if (!userAnswer) {
            alert("⚠️ Please enter an answer.");
            return;
        }
    
        if (answerShown) {
            alert("⚠️ Answer was already shown! No points awarded.");
            return;
        }
    
        // ✅ Prevent duplicate answers for the same question
        if (puzzleQuestion === previousQuestion) {
            alert("⚠️ You have already answered this question!");
            return;
        }
        previousQuestion = puzzleQuestion;
    
        const response = await fetch(`${API_BASE_URL}/api/puzzles/check-answer`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                question: puzzleQuestion,
                expected_answer: correctAnswer,
                guessed_answer: userAnswer,
                puzzleType,
                modelName: "gpt-3.5-turbo"
            })
        });
    
        const data = await response.json();
        let userScore = parseInt(localStorage.getItem("userScore")) || 0;
    
        if (data.correct) {
            userScore += 10; // ✅ Increase user score
            localStorage.setItem("userScore", userScore);
            alert(`✅ Correct! +10 points\nYour Score: ${userScore}`);
        } else {
            alert(`❌ Incorrect!`);
    
            document.getElementById("correctAnswer").style.display = "block";
            document.getElementById("correctAnswer").innerHTML = `
                <p style="color: red;">
                    Input Answer: <strong>${userAnswer || "No input provided"}</strong><br>
                    Correct Answer: <strong>${correctAnswer}</strong>
                </p>
            `;
        }
        document.getElementById("userAnswer").value = "";
        updateScoreDisplay();
    }
    

    async function askDeepSeek() {
        const puzzleQuestion = document.getElementById("puzzleContainer").textContent;
        const correctAnswer = document.getElementById("userAnswer").dataset.correctAnswer;
        const puzzleType = document.getElementById("puzzleType").value;

        const response = await fetch(`${API_BASE_URL}/api/puzzles/generate-answer`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ question: puzzleQuestion, puzzleType, modelName: "deepseek" })
        });

        const data = await response.json();
        const deepSeekAnswer = data.answer || "No answer received";

        // Validate DeepSeek's answer
        const validationResponse = await fetch(`${API_BASE_URL}/api/puzzles/check-answer`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ 
                question: puzzleQuestion, 
                expected_answer: correctAnswer, 
                guessed_answer: deepSeekAnswer, 
                puzzleType, 
                modelName: "deepseek" 
            })
        });

        const validationData = await validationResponse.json();

        let deepSeekScore = parseInt(localStorage.getItem("deepSeekScore")) || 0;
        if (validationData.correct) {
            alert("DeepSeek solved it correctly! +10 points to DeepSeek.");
            document.getElementById("correctAnswer").style.display = "block";
            document.getElementById("correctAnswer").innerHTML = `
                <p style="color: red;">
                    DeepSeek's Answer: <strong>${deepSeekAnswer}</strong><br>
                    Correct Answer: <strong>${correctAnswer}</strong>
                </p>
            `;
            deepSeekScore += 10; // ✅ Increase DeepSeek score
            localStorage.setItem("deepSeekScore", deepSeekScore);
        } else {
            alert("DeepSeek got it wrong!");
            document.getElementById("correctAnswer").style.display = "block";
            document.getElementById("correctAnswer").innerHTML = `
                <p style="color: red;">
                    DeepSeek's Answer: <strong>${deepSeekAnswer}</strong><br>
                    Correct Answer: <strong>${correctAnswer}</strong>
                </p>
            `;
        }
        updateScoreDisplay();
    }
});
