// TeaFabric desktop launcher: serves the web build next to it (./web) on 127.0.0.1 and opens it in the
// default browser, so the game runs without downloading it from the website every time.
//
// The port is fixed: the browser keeps worlds and settings per address (IndexedDB), so a different
// port would start with nothing saved.
package main

import (
	"bufio"
	"fmt"
	"io"
	"mime"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const (
	port = 47265
	id   = "teafabric-launcher"
)

func main() {
	root, err := webRoot()
	if err != nil {
		fail(err.Error())
	}
	for ext, typ := range map[string]string{
		".wasm": "application/wasm", ".js": "text/javascript; charset=utf-8", ".json": "application/json",
		".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".zip": "application/zip",
	} {
		_ = mime.AddExtensionType(ext, typ)
	}
	url := fmt.Sprintf("http://127.0.0.1:%d/", port)
	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		if alreadyRunning(url) {
			fmt.Println("TeaFabric 已經在執行，正在打開瀏覽器…")
			openBrowser(url)
			time.Sleep(3 * time.Second)
			return
		}
		fail(fmt.Sprintf("無法使用連接埠 %d（可能被其他程式佔用）：%v", port, err))
	}

	files := http.FileServer(http.Dir(root))
	mux := http.NewServeMux()
	mux.HandleFunc("/"+id, func(w http.ResponseWriter, r *http.Request) { io.WriteString(w, id) })
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-cache")
		files.ServeHTTP(w, r)
	})

	fmt.Println("TeaFabric 1.16.5")
	fmt.Println("遊戲網址：" + url)
	fmt.Println("正在打開瀏覽器。需要 Chrome / Edge 119+、Firefox 120+ 或 Safari 18.2+。")
	fmt.Println("玩的時候不要關閉這個視窗；關閉它就會結束。")
	go func() {
		time.Sleep(300 * time.Millisecond)
		openBrowser(url)
	}()
	if err := http.Serve(ln, mux); err != nil {
		fail(err.Error())
	}
}

// webRoot finds the web folder next to the executable (or the current directory).
func webRoot() (string, error) {
	var candidates []string
	if exe, err := os.Executable(); err == nil {
		if resolved, err := filepath.EvalSymlinks(exe); err == nil {
			exe = resolved
		}
		candidates = append(candidates, filepath.Join(filepath.Dir(exe), "web"))
	}
	if wd, err := os.Getwd(); err == nil {
		candidates = append(candidates, filepath.Join(wd, "web"))
	}
	for _, dir := range candidates {
		if _, err := os.Stat(filepath.Join(dir, "index.html")); err == nil {
			return dir, nil
		}
	}
	return "", fmt.Errorf("找不到 web 資料夾。請先把整個壓縮檔解壓縮，再從解壓出來的資料夾執行（web 資料夾要和這個程式放在一起）。")
}

func alreadyRunning(url string) bool {
	client := http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get(url + id)
	if err != nil {
		return false
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 64))
	return resp.StatusCode == 200 && strings.TrimSpace(string(body)) == id
}

func openBrowser(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	case "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	if err := cmd.Start(); err != nil {
		fmt.Println("無法自動打開瀏覽器，請手動打開：" + url)
	}
}

func fail(msg string) {
	fmt.Println("錯誤：" + msg)
	fmt.Println("按 Enter 結束…")
	bufio.NewReader(os.Stdin).ReadString('\n')
	os.Exit(1)
}
