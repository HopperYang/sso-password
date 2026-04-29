using System.IO;
using System.Net.Http;
using System.Net.Http.Json;
using System.Security.Principal;
using System.Text.Json.Serialization;
using System.Windows;
using Microsoft.Extensions.Configuration;

namespace PocWpf;

public partial class MainWindow : Window
{
    private static readonly HttpClient Http = new();

    public MainWindow()
    {
        InitializeComponent();
        Loaded += OnLoaded;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        try
        {
            var cfg = new ConfigurationBuilder()
                .SetBasePath(AppContext.BaseDirectory)
                .AddJsonFile("appsettings.json", optional: false, reloadOnChange: true)
                .Build();

            string apiBase = cfg["Sso:ApiBase"] ?? "http://localhost:8080";
            string apiKey = cfg["Sso:ApiKey"] ?? "";
            string webUrl = cfg["Poc:WebUrl"] ?? "http://localhost:5173";
            string employeeId = cfg["Poc:EmployeeId"] ?? "E0001";
            string displayName = cfg["Poc:DisplayName"] ?? "User";

            if (bool.TryParse(cfg["Poc:PreferWindowsIdentityName"], out bool preferWin) && preferWin)
            {
                using WindowsIdentity? id = WindowsIdentity.GetCurrent();
                if (id?.Name is { Length: > 0 } name)
                {
                    displayName = name;
                }
            }

            using var req = new HttpRequestMessage(HttpMethod.Post, new Uri(new Uri(apiBase), "/api/auth/token"));
            req.Headers.Add("X-Api-Key", apiKey);
            req.Content = JsonContent.Create(new TokenIssueRequest(employeeId, displayName));

            using HttpResponseMessage resp = await Http.SendAsync(req);
            resp.EnsureSuccessStatusCode();
            TokenIssueResponse? body = await resp.Content.ReadFromJsonAsync<TokenIssueResponse>();
            if (string.IsNullOrEmpty(body?.AccessToken))
            {
                throw new IOException("empty token");
            }

            string target = $"{webUrl.TrimEnd('/')}/?token={Uri.EscapeDataString(body.AccessToken)}";
            await Web.EnsureCoreWebView2Async();
            Web.Source = new Uri(target);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"启动失败：{ex.Message}", "SSO POC", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private sealed record TokenIssueRequest(
        [property: JsonPropertyName("employeeId")] string EmployeeId,
        [property: JsonPropertyName("displayName")] string DisplayName);

    private sealed record TokenIssueResponse(
        [property: JsonPropertyName("accessToken")] string AccessToken);
}
