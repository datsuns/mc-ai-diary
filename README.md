# AI Diary Mod

[日本語の説明はこちら](./README.ja.md)

Generate Diary by Gemini AI.  
Generated Diary will be posted as chat.

![Play Screen](./images/screenshot.png)

# Setup

You need 3 procedures to user this mod. 
+ Install dependency
+ Generate Gemini API key
+ Set API key to this mod

## Install dependency

please install followings:

* [fabric](https://modrinth.com/mod/fabric-api)
* [cloth-config](https://modrinth.com/mod/cloth-config)

## Generate Gemini API 

* prepare your Google account
* Access to [Google AI Studio](https://aistudio.google.com/)
* Select `Get API key`, and `Create API Key`
  * ![Select API Key](./images/setup_01_select_get_api_key.png)
* Select `Create API key in new project`
  * ![Create API key](./images/setup_02_generate_api_key.png)
* Copy Generated API key.
  * ![save API Key](./images/setup_03_copy_api_key.png)
  * **NOTE:** Don't share this API key to ANYONE.

## Set API key to this mod

* Install this mod, and once start Minecraft.
* Open environment folder of Minecraft, and open `config` folder.
   * ![config location](./images/config_01_location.png)
* Check `aidiary.toml` file exists. Open it. (this file is Text file.)
  * ![the config file](./images/config_02_config_file.png)
* Set API key into the line start as `GeminiApikey = `
  * ![the config file](./images/config_03_replace_api_key.png)
* Restart Minecraft
  * **NOTE:** This configuration will be applyed after RESTARTED

# Note

* This mod relies on the Gemini API being available for free.  
  so, this mod may stop working if the API becomes paid.
