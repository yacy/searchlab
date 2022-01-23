# with Nix installed, you get an environment with everything needed to compile the searchlab by running:
# $ nix-shell

{ pkgs ? import <nixpkgs> {} }:

with pkgs;

let
  inherit (pkgs) stdenv bash platforms fetchFromGitHub jdk8_headless mkdocs;
  version = "0.121";
  pname = "searchlab";
  componentName = "${pname}-server";

in {
  "${componentName}" = stdenv.mkDerivation {
    name = "searchlab-env";
    src = fetchFromGitHub {
      owner = "yacy";
      repo = pname;
      rev = version;
      sha256 = "sha256-0y7zzFy1MXcm3XE8gZ6Nkq25FNoefcNk4W5YIWkKil0=";
    };
    buildInputs = [ jdk8_headless mkdocs ];
    
    meta = {
      description = "YaCy Searchlab - Search as Data Science";
      homepage = "https://searchlab.eu";
      longDescription = ''
        YaCy Searchlab is an extension to the existing YaCy Grid Crawler technology.
        YaCy Grid has no user GUI interface and no service management integrated so far.
        Searchlab is extending that software with the required elements to make it possible
        for users to sign up for web crawling tasks, do monitoring, using search applications
        and other tasks that are required for such a platform like
        user self-care and subscription management.
      '';
      maintainers = [ "orbiter" ];
    };
  };
}
