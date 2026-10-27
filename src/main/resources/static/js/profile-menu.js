export function createProfileMenu() {
  const trigger = document.getElementById('profile-menu-trigger');
  const menu = document.getElementById('profile-menu');
  const avatar = document.getElementById('avatar');
  const fallback = document.getElementById('avatar-fallback');
  if (!trigger || !menu) return { setProfile() {} };

  const close = (restoreFocus = false) => {
    menu.hidden = true;
    trigger.setAttribute('aria-expanded', 'false');
    if (restoreFocus) trigger.focus();
  };

  trigger.addEventListener('click', () => {
    const expanded = trigger.getAttribute('aria-expanded') === 'true';
    menu.hidden = expanded;
    trigger.setAttribute('aria-expanded', String(!expanded));
    if (!expanded) menu.querySelector('[role="menuitem"]:not([hidden])')?.focus();
  });

  document.addEventListener('pointerdown', (event) => {
    if (!menu.hidden && !menu.contains(event.target) && !trigger.contains(event.target)) close();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !menu.hidden) close(true);
  });

  menu.querySelectorAll('a, button').forEach((item) => item.addEventListener('click', () => close()));

  return {
    setProfile(profile) {
      const hasAvatar = Boolean(profile?.avatar);
      if (avatar) {
        avatar.hidden = !hasAvatar;
        if (hasAvatar && avatar.src !== profile.avatar) avatar.src = profile.avatar;
      }
      if (fallback) fallback.hidden = hasAvatar;
      trigger.setAttribute('aria-label', profile?.name
        ? `Menu du profil ${profile.name}`
        : 'Menu du profil');
      const menuConfiguration = document.getElementById('menu-configuration');
      const recapture = document.getElementById('recapture-profile');
      const logout = document.getElementById('logout');
      const createProfile = document.getElementById('create-profile');
      const unrecognizedHelp = document.getElementById('unrecognized-help');
      if (menuConfiguration) menuConfiguration.hidden = !profile;
      if (recapture) recapture.hidden = !profile;
      if (logout) logout.hidden = !profile;
      if (createProfile) createProfile.hidden = Boolean(profile);
      if (unrecognizedHelp) unrecognizedHelp.hidden = Boolean(profile);
    },
  };
}