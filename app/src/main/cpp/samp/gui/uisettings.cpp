#define IMGUI_DEFINE_MATH_OPERATORS
#include "../vendor/imgui/imgui.h"
#include "../vendor/imgui/imgui_internal.h"

#include <string>
#include <algorithm>
#include "uisettings.h"
#include "../main.h"
#include "../settings.h"

extern CSettings* pSettings;

/* scaling */
ImVec2 UISettings::m_baseSize = ImVec2(640.0f, 480.0f);
ImVec2 UISettings::m_scaleFactor = ImVec2(1.0f, 1.0f);

/* font */
float UISettings::m_fontSize = 30.0f;//26.0f;//24.0f;
float UISettings::m_outlineSize = 2.0f;

/* ////////////////// pos & size ////////////////// */

/* widgets */
float UISettings::m_padding = 15.0f;

/* keyboard */
ImVec2 UISettings::m_keyboardPos = ImVec2(0.0f, 180.0f/*180.0f*/);
ImVec2 UISettings::m_keyboardSize = ImVec2(640.0f, 300.0f);//ImVec2(640.0f, 300.0f);
float UISettings::m_keyboardRowHeight = 52.0f;//52.0f;

/* splashscreen */
ImVec2 UISettings::m_splashscreenLogoPos;
ImVec2 UISettings::m_splashscreenLogoSize = ImVec2(640.0f, 480.0f);
ImVec2 UISettings::m_splashScreenPBarPos = ImVec2(100.0f, 460.0f);//ImVec2(100.0f, 380.0f);
ImVec2 UISettings::m_splashScreenPBarSize = ImVec2(440.0f, 10.0f);

/* chat */
int UISettings::m_chatDispMesssages = 9;
int UISettings::m_chatMaxMessages = 30;
ImVec2 UISettings::m_chatPos = ImVec2(100.0f, 10.0f);
ImVec2 UISettings::m_chatSize = ImVec2(400.0f, 0.0f);
ImVec2 UISettings::m_chatItemSize = ImVec2(400.0f, 13.0f);

/* spawn */
ImVec2 UISettings::m_spawnPos = ImVec2(200.0f, 420.0f);
ImVec2 UISettings::m_spawnSize = ImVec2(240.0f, 50.0f);

/* nametag */
ImVec2 UISettings::m_nametagBarSize = ImVec2(25.0f, 6.0f);

/* dialog */
ImVec2 UISettings::m_dialogButtonPanelSize = ImVec2(150.0f, 45.0f);
ImVec2 UISettings::m_dialogMinSize = ImVec2(150.0f, 120.0f);
ImVec2 UISettings::m_dialogMaxSize = ImVec2(620.0f, 400.0f);
float UISettings::m_dialogTitleHeight = 20.0f;
float UISettings::m_dialogListItemHeight = 30.0f;

/* buttonpanel */
ImVec2 UISettings::m_buttonPanelPos = ImVec2(5.0f, 140.0f/*150.0f*/);
ImVec2 UISettings::m_buttonPanelSize = ImVec2(380.0f/*250.0f*//*180.0f*//*250.0f*/, 50.0f); //ImVec2(120.0f, 45.0f);

/* voice button */
ImVec2 UISettings::m_buttonVoicePos = ImVec2(500.0f, 180.0f/*170.0f*/);
ImVec2 UISettings::m_buttonVoiceSize = ImVec2(55.0f, 80.0f);

/* ////////////////// colors ////////////////// */

/* button */
ImColor UISettings::m_buttonColor = ImColor(0.11f, 0.11f, 0.11f, 0.80f);
ImColor UISettings::m_buttonFocusedColor = ImColor(0x64, 0x95, 0xED);/*ImColor(119, 4, 4, 255);*/ //ImColor(80, 80, 80);

/* keyboard */
ImColor UISettings::m_keyboardBackgroundColor = ImColor(0, 0, 0, 150);

/* dialog */
ImColor UISettings::m_dialogBackgroundColor = ImColor(0, 0, 0, 200);
ImColor UISettings::m_dialogTitleBackgroundColor = ImColor(0, 0, 0, 200);/*ImColor(0xF5, 0x91, 0x32);*/// ImColor(50, 50, 50, 255);

void UISettings::Initialize(const ImVec2& display_size)
{
	if(!pSettings) return;
	
	m_scaleFactor = display_size / m_baseSize;

	const stSettings& settings = pSettings->Get();

	/* chat line count and font */
	m_chatDispMesssages = std::clamp(settings.iChatMaxMessages, 1, 30);
	m_chatMaxMessages = m_chatDispMesssages;
	m_fontSize = std::clamp(settings.fFontSize, 8.0f, 72.0f) * m_scaleFactor.y;

	/* keyboard */
	m_keyboardSize = m_keyboardSize * m_scaleFactor;
	m_keyboardPos = m_keyboardPos * m_scaleFactor;
	m_keyboardRowHeight *= m_scaleFactor.y;

	/* splashscreen */
	m_splashscreenLogoSize = m_splashscreenLogoSize * m_scaleFactor;
	m_splashscreenLogoPos = ImVec2(0.0f, 0.0f);
	m_splashScreenPBarPos = m_splashScreenPBarPos * m_scaleFactor;
	m_splashScreenPBarSize = m_splashScreenPBarSize * m_scaleFactor;

	/* chat: settings.ini coordinates use a 1920x1080 reference canvas. */
	const float chatX = std::clamp(settings.fChatPosX, 0.0f, 1920.0f) * (m_baseSize.x / 1920.0f);
	const float chatY = std::clamp(settings.fChatPosY, 0.0f, 1080.0f) * (m_baseSize.y / 1080.0f);
	const float chatWidth = std::clamp(settings.fChatSizeX, 120.0f, 1920.0f) * (m_baseSize.x / 1920.0f);
	const float chatHeight = std::clamp(settings.fChatSizeY, 36.0f, 1080.0f) * (m_baseSize.y / 1080.0f);
	m_chatPos = ImVec2(chatX * m_scaleFactor.x, chatY * m_scaleFactor.y);
	m_chatSize = ImVec2(chatWidth * m_scaleFactor.x, chatHeight * m_scaleFactor.y);
	m_chatItemSize = ImVec2(m_chatSize.x, m_chatSize.y / static_cast<float>(m_chatDispMesssages));

	/* spawn */
	m_spawnPos = m_spawnPos * m_scaleFactor;
	m_spawnSize = m_spawnSize * m_scaleFactor;

	/* nametag */
	m_nametagBarSize = m_nametagBarSize * m_scaleFactor;

	/* dialog */
	m_dialogButtonPanelSize = m_dialogButtonPanelSize * m_scaleFactor;
	m_dialogMinSize = m_dialogMinSize * m_scaleFactor;
	m_dialogMaxSize = m_dialogMaxSize * m_scaleFactor;
	m_dialogTitleHeight *= m_scaleFactor.y;
	m_dialogListItemHeight *= m_scaleFactor.y;

	/* buttonpanel */
	m_buttonPanelPos = m_buttonPanelPos * m_scaleFactor;
	m_buttonPanelSize = m_buttonPanelSize * m_scaleFactor;

	/* button voice */
	m_buttonVoicePos = m_buttonVoicePos * m_scaleFactor;
	m_buttonVoiceSize = m_buttonVoiceSize * m_scaleFactor;
}