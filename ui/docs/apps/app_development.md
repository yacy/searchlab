# Data Studio App Development

To start developing your own, just clone the repository from [https://github.com/yacy/searchlab_apps](https://github.com/yacy/searchlab_apps) 
and open any of the `index.html` files within the app project inside the `htdocs` folder.

Each of those apps should work fine without hosting the html pages with
a web server. Just open the corresponding `index.html` in your browser for
testing. You also don't need to run your own Searchlab instance, the apps
connect to the server at https://searchlab.eu

If you want to develop your own search apps, you can easily modify/extend
any of the given app.

We recommend to start with the `websearch_lit` app,
just make a copy of it!

## Integration with your own Searchlab instance

There is no need to install your own Searchlab instance for app development,
however if you contribute to the Searchlab APIs, then you must set-up your own
development environment.

- For Searchlab development and/or packaging, clone the repository [https://github.com/yacy/searchlab_apps](https://github.com/yacy/searchlab_apps)
  aside the repository of searchlab.
  The build process will expect that the searchlab_apps path is in parallel
- To use your own search server, run a searchlab instance and modify the path
  to your instance in `htdocs/js/config.js`.

## Contributing Your Own Apps
If you like please give us a pull request with your new app!
We love to extend the searchlab apps with community-created content.

To do so, please..
- Create a new subfolder within `htdocs` with the name of your app
- Create a app.json and fill it with an app description using at least
  the same fields as used in `htdocs/websearch_list/app.json`.
  The app.json is used within https://searchlab.eu to show a proper visualization
  of your app.
- You must create a `index.html` file within your app folder.
- You must create a `screenshot.png` file with the exact size of 1024x1024.
  The image should not contain any transparency and it should show a mostly
  proper screenshot of your app when it is producing something useful for the user.
- You can use all `css` and `js` code as given in `htdocs/css` and `htdocs/js`,
  but you *MUST NOT* add any files to those directories. If you need any other
  `css` and `js` code, please link them directly from the internet or add those
  to your app folder in a separate `css`/`js`-path within your app folder.
- Your App must be published under the CC0 license.
- Make a pull request where only files within your app folder is added/modified,
  not anything else.

Everything that is merged to this repository will be pushed to https://searchlab.eu
and can then be used there.
