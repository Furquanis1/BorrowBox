/**
 * BorrowBox auth page logic
 */

function toggleView(viewId) {
  const views = document.querySelectorAll('.view');
  views.forEach(view => {
    view.classList.remove('active');
    setTimeout(() => {
      view.style.display = 'none';
    }, 50);
  });

  setTimeout(() => {
    const targetView = document.getElementById(viewId);
    targetView.style.display = 'block';
    void targetView.offsetWidth;
    targetView.classList.add('active');
  }, 50);
}

function setButtonState(button, disabled, text) {
  button.disabled = disabled;
  button.textContent = text;
}

async function handleSignIn(event) {
  event.preventDefault();
  const submitButton = event.target.querySelector('button[type="submit"]');
  const originalText = submitButton.textContent;
  setButtonState(submitButton, true, 'Signing in...');

  try {
    const email = document.getElementById('signinEmail').value.trim();
    const password = document.getElementById('signinPassword').value;
    const auth = await api.login(email, password);
    setSession(auth.user, auth.token);
    api.setToken(auth.token);
    window.location.href = 'workspace.html';
  } catch (err) {
    setButtonState(submitButton, false, originalText);
    alert('Sign in failed: ' + err.message);
  }
}

async function handleSignUp(event) {
  event.preventDefault();
  const submitButton = event.target.querySelector('button[type="submit"]');
  const originalText = submitButton.textContent;
  setButtonState(submitButton, true, 'Creating account...');

  try {
    const fullName = document.getElementById('signupName').value.trim();
    const email = document.getElementById('signupEmail').value.trim();
    const password = document.getElementById('signupPassword').value;

    if (!fullName || !email || !password) {
      throw new Error('Please complete all fields.');
    }

    const auth = await api.createUser(fullName, email, password);
    setSession(auth.user, auth.token);
    api.setToken(auth.token);
    window.location.href = 'workspace.html';
  } catch (err) {
    setButtonState(submitButton, false, originalText);
    alert('Sign up failed: ' + err.message);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const signinView = document.getElementById('signinView');
  const signupView = document.getElementById('signupView');
  if (signinView) signinView.classList.add('active');
  if (signupView) signupView.classList.remove('active');
});
