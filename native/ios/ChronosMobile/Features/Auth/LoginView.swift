import SwiftUI

struct LoginView: View {
    @EnvironmentObject var model: AppModel
    @State var username = ""
    @State var password = ""
    @State var passwordVisible = false
    @FocusState var focusedField: LoginField?

    let portalGreen = Color(red: 8 / 255, green: 123 / 255, blue: 104 / 255)
    let portalDark = Color(red: 6 / 255, green: 61 / 255, blue: 57 / 255)

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color(red: 244 / 255, green: 247 / 255, blue: 248 / 255)
                    .ignoresSafeArea()

                Circle()
                    .fill(portalGreen.opacity(0.08))
                    .frame(width: 300, height: 300)
                    .blur(radius: 2)
                    .offset(x: geometry.size.width * 0.42, y: -geometry.size.height * 0.42)

                Circle()
                    .fill(portalGreen.opacity(0.05))
                    .frame(width: 230, height: 230)
                    .offset(x: -geometry.size.width * 0.42, y: geometry.size.height * 0.43)

                ScrollView {
                    VStack(spacing: 0) {
                        brandSection
                            .padding(.top, max(32, geometry.safeAreaInsets.top + 24))

                        loginCard
                            .padding(.top, 34)

                        footer
                            .padding(.top, 28)
                            .padding(.bottom, max(24, geometry.safeAreaInsets.bottom + 16))
                    }
                    .padding(.horizontal, 24)
                    .frame(maxWidth: 520)
                    .frame(
                        minHeight: geometry.size.height,
                        alignment: geometry.size.height < 700 ? .top : .center
                    )
                }
                .scrollDismissesKeyboard(.interactively)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .preferredColorScheme(.light)
    }

    var brandSection: some View {
        VStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 19, style: .continuous)
                    .fill(
                        LinearGradient(
                            colors: [portalGreen, portalDark],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 68, height: 68)
                    .shadow(color: portalGreen.opacity(0.25), radius: 20, y: 10)

                Text("C")
                    .font(.system(size: 34, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
            }

            VStack(spacing: 7) {
                Text("CHRONOS")
                    .font(.system(size: 13, weight: .bold, design: .rounded))
                    .tracking(4)
                    .foregroundStyle(portalGreen)

                Text("连接校园每一项工作")
                    .font(.system(size: 25, weight: .bold))
                    .foregroundStyle(Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255))

                Text("统一待办 · 智能办公 · 安全协同")
                    .font(.system(size: 14))
                    .foregroundStyle(Color(red: 113 / 255, green: 128 / 255, blue: 138 / 255))
            }
        }
        .frame(maxWidth: .infinity)
    }

    var loginCard: some View {
        VStack(alignment: .leading, spacing: 22) {
            VStack(alignment: .leading, spacing: 7) {
                Text("欢迎回来")
                    .font(.system(size: 27, weight: .bold))
                    .foregroundStyle(Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255))
                Text("登录 Chronos 统一门户")
                    .font(.system(size: 14))
                    .foregroundStyle(Color(red: 122 / 255, green: 135 / 255, blue: 145 / 255))
            }

            VStack(spacing: 15) {
                loginField(
                    title: "账号",
                    icon: "person",
                    text: $username,
                    field: .username
                )
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textContentType(.username)
                .submitLabel(.next)
                .onSubmit { focusedField = .password }

                passwordField
            }

            if let error = model.error {
                Label(error, systemImage: "exclamationmark.circle.fill")
                    .font(.system(size: 13))
                    .foregroundStyle(Color(red: 183 / 255, green: 61 / 255, blue: 61 / 255))
                    .fixedSize(horizontal: false, vertical: true)
            }

            Button(action: submit) {
                HStack(spacing: 10) {
                    if model.isLoggingIn {
                        ProgressView()
                            .tint(.white)
                    }
                    Text(model.isLoggingIn ? "正在登录…" : "登录统一门户")
                        .font(.system(size: 16, weight: .bold))
                    if !model.isLoggingIn {
                        Image(systemName: "arrow.right")
                            .font(.system(size: 14, weight: .bold))
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .foregroundStyle(.white)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(canSubmit ? portalGreen : Color.gray.opacity(0.35))
                )
                .shadow(color: canSubmit ? portalGreen.opacity(0.22) : .clear, radius: 14, y: 7)
            }
            .buttonStyle(.plain)
            .disabled(!canSubmit || model.isLoggingIn)
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(.white)
                .shadow(color: Color.black.opacity(0.07), radius: 28, y: 12)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(Color(red: 225 / 255, green: 231 / 255, blue: 235 / 255), lineWidth: 1)
        )
    }

    func loginField(
        title: String,
        icon: String,
        text: Binding<String>,
        field: LoginField
    ) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(focusedField == field ? portalGreen : Color.gray)
                .frame(width: 22)
            TextField(title, text: text)
                .focused($focusedField, equals: field)
                .font(.system(size: 16))
                .foregroundStyle(Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255))
        }
        .padding(.horizontal, 16)
        .frame(height: 54)
        .background(fieldBackground(isFocused: focusedField == field))
    }

    var passwordField: some View {
        HStack(spacing: 12) {
            Image(systemName: "lock")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(focusedField == .password ? portalGreen : Color.gray)
                .frame(width: 22)

            Group {
                if passwordVisible {
                    TextField("密码", text: $password)
                } else {
                    SecureField("密码", text: $password)
                }
            }
            .focused($focusedField, equals: .password)
            .font(.system(size: 16))
            .foregroundStyle(Color(red: 23 / 255, green: 32 / 255, blue: 42 / 255))
            .textContentType(.password)
            .submitLabel(.go)
            .onSubmit(submit)

            Button {
                passwordVisible.toggle()
            } label: {
                Image(systemName: passwordVisible ? "eye.slash" : "eye")
                    .foregroundStyle(Color.gray)
                    .frame(width: 28, height: 40)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(passwordVisible ? "隐藏密码" : "显示密码")
        }
        .padding(.horizontal, 16)
        .frame(height: 54)
        .background(fieldBackground(isFocused: focusedField == .password))
    }

    func fieldBackground(isFocused: Bool) -> some View {
        RoundedRectangle(cornerRadius: 13, style: .continuous)
            .fill(Color(red: 248 / 255, green: 250 / 255, blue: 251 / 255))
            .overlay(
                RoundedRectangle(cornerRadius: 13, style: .continuous)
                    .stroke(
                        isFocused ? portalGreen : Color(red: 218 / 255, green: 226 / 255, blue: 229 / 255),
                        lineWidth: isFocused ? 1.5 : 1
                    )
            )
    }

    var footer: some View {
        VStack(spacing: 5) {
            Label("安全连接由 Chronos 统一身份认证提供", systemImage: "checkmark.shield")
                .font(.system(size: 12))
                .foregroundStyle(Color(red: 122 / 255, green: 135 / 255, blue: 145 / 255))
            Text("© 2026 Chronos")
                .font(.system(size: 11))
                .foregroundStyle(Color(red: 160 / 255, green: 170 / 255, blue: 177 / 255))
        }
        .frame(maxWidth: .infinity)
    }

    var canSubmit: Bool {
        !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !password.isEmpty
    }

    func submit() {
        guard canSubmit, !model.isLoggingIn else { return }
        focusedField = nil
        Task {
            await model.login(
                username: username.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
        }
    }
}

enum LoginField: Hashable {
    case username
    case password
}

